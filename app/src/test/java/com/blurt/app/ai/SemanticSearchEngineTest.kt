package com.blurt.app.ai

import androidx.room.Room
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.model.CaptureType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies the meaning-search layer against a real in-memory Room database
 * and a deterministic fake embedding provider (no network). The fake maps
 * words to fixed topic vectors, so "trip" reliably sits closer to a vacation
 * note than to a coding note — without depending on any real model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticSearchEngineTest {

    private lateinit var database: BlurtDatabase
    private lateinit var repository: CaptureRepository
    private lateinit var provider: FakeEmbeddingProvider
    private lateinit var engine: SemanticSearchEngine

    private val uid = "uid-semantic"

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, BlurtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CaptureRepository(database.captureDao())
        provider = FakeEmbeddingProvider()
        engine = SemanticSearchEngine(
            dao = database.captureDao(),
            embeddingDao = database.embeddingDao(),
            provider = provider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun meaningMatchesRankAboveKeywordUnrelated() = runTest {
        val vacationId = repository.create(uid, CaptureType.TEXT, "vacation in goa with friends")
        repository.create(uid, CaptureType.TEXT, "kotlin coroutines and room database code")
        repository.create(uid, CaptureType.TEXT, "pasta recipe with tomatoes")

        val results = engine.search("trip to the beach", uid)!!

        // No capture contains any query word, yet the vacation note wins.
        assertTrue(results.isNotEmpty())
        assertEquals(vacationId, results.first().id)
        assertTrue("all active captures should rank", results.size == 3)
    }

    @Test
    fun vectorsAreComputedOnceAndCached() = runTest {
        repository.create(uid, CaptureType.TEXT, "vacation in goa")
        repository.create(uid, CaptureType.TEXT, "kotlin code")

        engine.search("beach trip", uid)
        val capturesEmbeddedFirstRun = provider.embeddedDocuments.toList()

        engine.search("code quality", uid)
        engine.search("goa again", uid)

        // Every capture was embedded exactly once — later searches reused the cache.
        for (text in capturesEmbeddedFirstRun) {
            assertEquals(
                "capture '$text' should not be re-embedded",
                1,
                provider.embeddedDocuments.count { it == text },
            )
        }
    }

    @Test
    fun newCaptureIsBackfilledLazily() = runTest {
        repository.create(uid, CaptureType.TEXT, "vacation in goa")
        engine.search("beach", uid)

        repository.create(uid, CaptureType.TEXT, "notebook for drawing")
        engine.search("sketch", uid)

        val embedded = provider.embeddedDocuments
        assertTrue("new capture should be embedded on demand", "notebook for drawing" in embedded)
        assertEquals("only the new capture needs a vector", 1, embedded.count { it == "notebook for drawing" })
    }

    @Test
    fun editedCaptureIsReEmbedded() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "vacation in goa")
        engine.search("beach", uid)
        assertEquals(1, provider.embeddedDocuments.count { it == "vacation in goa" })

        repository.updateContent(id, uid, "vacation in goa, now with paragliding")
        engine.search("adventure", uid)

        // The edited content got a fresh vector because updatedAt advanced.
        assertTrue(provider.embeddedDocuments.contains("vacation in goa, now with paragliding"))
    }

    @Test
    fun providerFailureReturnsNullSoCallerFallsBack() = runTest {
        repository.create(uid, CaptureType.TEXT, "vacation in goa")
        provider.fail = true

        assertNull(engine.search("beach", uid))
    }

    @Test
    fun disabledEngineReturnsNull() = runTest {
        repository.create(uid, CaptureType.TEXT, "vacation in goa")
        val disabled = SemanticSearchEngine(database.captureDao(), database.embeddingDao(), null)

        assertNull(disabled.search("beach", uid))
    }

    @Test
    fun deletedCaptureVectorsAreIgnored() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "vacation in goa")
        repository.create(uid, CaptureType.TEXT, "kotlin code")
        engine.search("beach", uid)

        // Tombstone the vacation note: its cached vector must not leak into results.
        repository.delete(id, uid)

        val results = engine.search("beach trip", uid)!!
        assertEquals(1, results.size)
        assertEquals("kotlin code", results.single().content)
    }

    @Test
    fun clearForUserDropsAllVectors() = runTest {
        repository.create(uid, CaptureType.TEXT, "vacation in goa")
        engine.search("beach", uid)
        assertNotNull(engine.search("trip", uid))

        engine.clearForUser(uid)

        // No cached vectors → full backfill needed on the next search.
        provider.embeddedDocuments.clear()
        engine.search("trip", uid)
        assertTrue(provider.embeddedDocuments.contains("vacation in goa"))
    }
}

/**
 * Deterministic stand-in for Gemini: each word maps to a fixed topic vector,
 * a document's vector is the sum of its words. "vacation/beach/trip" share a
 * topic, "kotlin/code" share another, so similarity is meaningful and stable.
 */
private class FakeEmbeddingProvider : EmbeddingProvider {

    val embeddedDocuments = mutableListOf<String>()
    var fail = false

    override suspend fun embed(texts: List<String>): List<FloatArray>? {
        if (fail) throw RuntimeException("quota exceeded")
        return texts.map { text ->
            embeddedDocuments += text
            vectorFor(text)
        }
    }

    private fun vectorFor(text: String): FloatArray {
        val out = FloatArray(4)
        text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }.forEach { word ->
            out[0] += 0.5f // common component: everything is slightly related
            out[1 + topicOf(word)] += 1f
        }
        if (out.sum() == 0f) out[0] = 0.1f
        return out
    }

    /** Fixed vocabulary → topic, so ranking is fully deterministic. */
    private fun topicOf(word: String): Int = when (word) {
        "vacation", "goa", "trip", "beach", "travel", "paragliding", "adventure" -> 0
        "kotlin", "code", "room", "database", "coroutines", "quality" -> 1
        "pasta", "recipe", "tomatoes", "cooking", "sketch", "notebook", "drawing" -> 2
        else -> 1
    }
}
