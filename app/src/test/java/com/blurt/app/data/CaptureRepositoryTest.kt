package com.blurt.app.data

import android.net.Uri
import androidx.room.Room
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.local.SyncState
import com.blurt.app.data.model.CaptureType
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the repository against a real in-memory SQLite database, covering
 * create, ordering, update, delete, search (including LIKE escaping), and
 * per-user data isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureRepositoryTest {

    private lateinit var database: BlurtDatabase
    private lateinit var repository: CaptureRepository

    private val alice = "uid-alice"
    private val bob = "uid-bob"

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, BlurtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CaptureRepository(database.captureDao(), ImageStore(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- create ---------------------------------------------------------------

    @Test
    fun create_persistsCaptureAndReturnsPositiveId() = runTest {
        val id = repository.create(alice, CaptureType.TEXT, "hello world", null)

        assertTrue(id > 0)
        val captures = repository.observeAll(alice).first()
        assertEquals(1, captures.size)
        assertEquals("hello world", captures[0].content)
        assertEquals(CaptureType.TEXT, captures[0].type)
    }

    @Test
    fun create_stampsRemoteIdAndPendingSyncState() = runTest {
        val id = repository.create(alice, CaptureType.TEXT, "sync me", null)

        val entity = database.captureDao().getById(id, alice)!!
        assertNotNull(entity.remoteId)
        assertEquals(SyncState.PENDING, entity.syncState)
    }

    @Test
    fun create_trimsContentAndCopiesImageIntoAppStorage() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val source = Uri.parse("content://media/picker/1")
        shadowOf(context.contentResolver)
            .registerInputStream(source, ByteArrayInputStream("fake-image-bytes".toByteArray()))

        val id = repository.create(alice, CaptureType.IMAGE, "  caption here  ", source)

        val capture = repository.observeById(id, alice).first()
        assertNotNull(capture)
        assertEquals("caption here", capture?.content)
        // The picked content:// URI must NOT be stored as-is; it is copied into
        // app storage so the note survives reboots.
        val storedUri = capture?.imageUri
        assertNotNull(storedUri)
        assertEquals("file", storedUri?.scheme)
        val file = File(storedUri!!.path!!)
        assertTrue(file.exists())
        assertEquals("fake-image-bytes", file.readText())
    }

    @Test
    fun observeAll_returnsNewestFirst() = runTest {
        val first = repository.create(alice, CaptureType.TEXT, "oldest", null)
        val second = repository.create(alice, CaptureType.TEXT, "middle", null)
        val third = repository.create(alice, CaptureType.TEXT, "newest", null)

        val captures = repository.observeAll(alice).first()
        assertEquals(listOf(third, second, first), captures.map { it.id })
    }

    // --- data isolation -------------------------------------------------------

    @Test
    fun observeAll_neverReturnsAnotherUsersCaptures() = runTest {
        repository.create(alice, CaptureType.TEXT, "alice's private note", null)
        repository.create(bob, CaptureType.TEXT, "bob's private note", null)

        val aliceSees = repository.observeAll(alice).first()
        val bobSees = repository.observeAll(bob).first()

        assertEquals(listOf("alice's private note"), aliceSees.map { it.content })
        assertEquals(listOf("bob's private note"), bobSees.map { it.content })
    }

    @Test
    fun observeById_returnsNullForAnotherUsersCapture() = runTest {
        val aliceId = repository.create(alice, CaptureType.TEXT, "alice's note", null)

        assertNotNull(repository.observeById(aliceId, alice).first())
        assertNull(repository.observeById(aliceId, bob).first())
    }

    @Test
    fun search_isScopedToTheOwner() = runTest {
        repository.create(alice, CaptureType.TEXT, "alice writes about AI", null)
        repository.create(bob, CaptureType.TEXT, "bob writes about AI", null)

        val aliceHits = repository.search("AI", alice).first()
        assertEquals(1, aliceHits.size)
        assertEquals("alice writes about AI", aliceHits[0].content)
    }

    @Test
    fun update_cannotTouchAnotherUsersCapture() = runTest {
        val aliceId = repository.create(alice, CaptureType.TEXT, "alice's note", null)

        repository.updateContent(aliceId, bob, "bob hacked this")

        assertEquals("alice's note", repository.observeById(aliceId, alice).first()?.content)
    }

    @Test
    fun delete_cannotRemoveAnotherUsersCapture() = runTest {
        val aliceId = repository.create(alice, CaptureType.TEXT, "alice's note", null)

        repository.delete(aliceId, bob)

        assertNotNull(repository.observeById(aliceId, alice).first())
    }

    // --- claim-unowned (pre-auth legacy data) ---------------------------------

    @Test
    fun claimUnowned_assignsLegacyRowsToTheFirstUserOnly() = runTest {
        // Rows inserted directly with no owner, as if created before auth.
        database.captureDao().insert(
            com.blurt.app.data.local.CaptureEntity(
                ownerId = null,
                content = "legacy note",
                type = CaptureType.TEXT,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        repository.create(alice, CaptureType.TEXT, "already owned", null)

        repository.claimUnowned(alice)

        val aliceSees = repository.observeAll(alice).first().map { it.content }
        assertEquals(setOf("legacy note", "already owned"), aliceSees.toSet())
        // Bob sees nothing — the legacy row was claimed by Alice, not shared.
        assertTrue(repository.observeAll(bob).first().isEmpty())
    }

    // --- update / delete ------------------------------------------------------

    @Test
    fun updateContent_modifiesContentAndBumpsTimestamp() = runTest {
        val id = repository.create(alice, CaptureType.TEXT, "before", null)
        val before = repository.observeById(id, alice).first()!!

        repository.updateContent(id, alice, "after")

        val after = repository.observeById(id, alice).first()!!
        assertEquals("after", after.content)
        assertTrue(after.updatedAt >= before.updatedAt)
    }

    @Test
    fun updateContent_unknownIdIsNoOp() = runTest {
        repository.updateContent(999L, alice, "nope")

        assertEquals(0, repository.observeAll(alice).first().size)
    }

    @Test
    fun updateContent_flipsSyncedRowBackToPending() = runTest {
        val id = repository.create(alice, CaptureType.TEXT, "original", null)
        val entity = database.captureDao().getById(id, alice)!!
        database.captureDao().markSynced(entity.remoteId!!, null)
        assertEquals(SyncState.SYNCED, database.captureDao().getById(id, alice)!!.syncState)

        repository.updateContent(id, alice, "edited")

        assertEquals(SyncState.PENDING, database.captureDao().getById(id, alice)!!.syncState)
    }

    @Test
    fun delete_removesOnlyTheTargetRecord() = runTest {
        val keep = repository.create(alice, CaptureType.IDEA, "keep me", null)
        val drop = repository.create(alice, CaptureType.IDEA, "drop me", null)

        repository.delete(drop, alice)

        val captures = repository.observeAll(alice).first()
        assertEquals(listOf(keep), captures.map { it.id })
        assertNull(repository.observeById(drop, alice).first())
    }

    @Test
    fun delete_tombstonesTheRowSoItCanSync() = runTest {
        val id = repository.create(alice, CaptureType.TEXT, "bye", null)

        repository.delete(id, alice)

        // Hidden from every user-facing query…
        assertTrue(repository.observeAll(alice).first().isEmpty())
        // …but still present as a tombstone for the sync engine to drain.
        val entity = database.captureDao().getByIdIncludingDeleted(id)
        assertNotNull(entity)
        assertNotNull(entity!!.deletedAt)
    }

    @Test
    fun delete_removesTheStoredImageFile() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val source = Uri.parse("content://media/picker/1")
        shadowOf(context.contentResolver)
            .registerInputStream(source, ByteArrayInputStream("bytes".toByteArray()))

        val id = repository.create(alice, CaptureType.IMAGE, "caption", source)
        val imagesDir = File(context.filesDir, "images")
        val storedFiles = imagesDir.listFiles() ?: emptyArray()
        assertEquals(1, storedFiles.size)
        assertTrue(storedFiles[0].exists())

        repository.delete(id, alice)

        // The copied image file must be cleaned up with the note. (Asserted via
        // the filesystem rather than the stored URI string because URI path
        // round-tripping is not portable across host OSes in Robolectric.)
        assertTrue((imagesDir.listFiles() ?: emptyArray()).isEmpty())
    }

    // --- search ---------------------------------------------------------------

    @Test
    fun search_isCaseInsensitiveAndPartial() = runTest {
        repository.create(alice, CaptureType.TEXT, "Blurt text about AI agents", null)
        repository.create(alice, CaptureType.TEXT, "completely different", null)

        val hits = repository.search("blurt", alice).first()
        assertEquals(1, hits.size)
        assertEquals("Blurt text about AI agents", hits[0].content)
    }

    @Test
    fun search_matchesLinkContentCaseInsensitively() = runTest {
        repository.create(alice, CaptureType.LINK, "https://voiceos.com", null)

        assertEquals(1, repository.search("voiceos", alice).first().size)
        assertEquals(1, repository.search("VOICEOS", alice).first().size)
    }

    @Test
    fun search_escapesPercentWildcard() = runTest {
        repository.create(alice, CaptureType.TEXT, "Progress 100% done", null)
        repository.create(alice, CaptureType.TEXT, "100 bananas", null)

        // Unescaped, "100%" would match both rows (percent = any suffix).
        val hits = repository.search("100%", alice).first()
        assertEquals(1, hits.size)
        assertEquals("Progress 100% done", hits[0].content)
    }

    @Test
    fun search_escapesUnderscoreWildcard() = runTest {
        repository.create(alice, CaptureType.TEXT, "snake_case note", null)
        repository.create(alice, CaptureType.TEXT, "snakeXcase note", null)

        // Unescaped, "_" would match any single character (both rows).
        val hits = repository.search("snake_case", alice).first()
        assertEquals(1, hits.size)
        assertEquals("snake_case note", hits[0].content)
    }

    @Test
    fun search_noMatchesReturnsEmptyList() = runTest {
        repository.create(alice, CaptureType.TEXT, "nothing to see here", null)

        assertTrue(repository.search("zzzzzz", alice).first().isEmpty())
    }

    @Test
    fun search_emptyQueryReturnsAllRows() = runTest {
        // The ViewModel guards blank queries; the repository contract is documented here.
        repository.create(alice, CaptureType.TEXT, "one", null)
        repository.create(alice, CaptureType.TEXT, "two", null)

        assertEquals(2, repository.search("", alice).first().size)
    }
}
