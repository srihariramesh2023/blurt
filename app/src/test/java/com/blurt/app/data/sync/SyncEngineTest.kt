package com.blurt.app.data.sync

import androidx.room.Room
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.SyncState
import com.blurt.app.data.model.CaptureType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * Verifies the sync engine's processing against a real in-memory Room database
 * and an in-memory fake backend: local uploads, remote merges, conflict
 * resolution, and delete propagation.
 *
 * The engine's flow wiring is thin glue; these tests drive the actual sync
 * pass ([SyncEngine.processOnce]) directly on the single test thread, which
 * is also what makes them deterministic under Robolectric (its SQLite
 * implementation is not multi-thread safe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private lateinit var database: BlurtDatabase
    private lateinit var dao: CaptureDao
    private lateinit var repository: CaptureRepository
    private lateinit var remote: FakeCaptureRemote
    private lateinit var engine: SyncEngine

    private val uid = "uid-sync"

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, BlurtDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.captureDao()
        repository = CaptureRepository(dao)
        remote = FakeCaptureRemote()
        engine = SyncEngine(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            dao = dao,
            remote = remote,
            authState = MutableStateFlow(com.blurt.app.auth.AuthState.SignedOut),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localCreateIsUploadedAndMarkedSynced() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "hello sync")

        engine.processOnce(uid, remote.observeAll(uid).first())

        assertEquals(listOf("hello sync"), remote.uploaded.map { it.content })
        val entity = dao.getById(id, uid)!!
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertNotNull(entity.remoteId)
    }

    @Test
    fun editReUploadsTheChangedContent() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "v1")
        engine.processOnce(uid, remote.observeAll(uid).first())
        assertEquals(SyncState.SYNCED, dao.getById(id, uid)!!.syncState)

        repository.updateContent(id, uid, "v2")
        engine.processOnce(uid, remote.observeAll(uid).first())

        assertEquals("v2", remote.uploaded.last().content)
        assertEquals(SyncState.SYNCED, dao.getById(id, uid)!!.syncState)
    }

    @Test
    fun remoteCaptureFromAnotherDeviceIsMergedIn() = runTest {
        remote.seedRemote(
            listOf(RemoteCapture("remote-doc-1", "written on my tablet", CaptureType.IDEA, 1000L, 1000L))
        )

        engine.processOnce(uid, remote.observeAll(uid).first())

        val merged = repository.observeAll(uid).first().single()
        assertEquals("remote-doc-1", merged.remoteId)
        assertEquals(CaptureType.IDEA, merged.type)
        // Downloaded rows are already synced — never re-uploaded.
        assertEquals(0, remote.uploaded.size)
    }

    @Test
    fun remoteDeleteRemovesLocalSyncedRow() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "doomed")
        val remoteId = dao.getById(id, uid)!!.remoteId!!
        engine.processOnce(uid, remote.observeAll(uid).first())
        assertEquals(1, repository.observeAll(uid).first().size)

        // The other device deletes it: the doc becomes an explicit tombstone.
        remote.seedRemote(
            listOf(RemoteCapture(remoteId, "doomed", CaptureType.TEXT, 1000L, 1000L, deleted = true))
        )
        engine.processOnce(uid, remote.observeAll(uid).first())

        assertTrue(repository.observeAll(uid).first().isEmpty())
        assertNull(dao.getByIdIncludingDeleted(id))
    }

    @Test
    fun staleSnapshotNeverDeletesFreshlyUploadedRow() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "keep me")

        // A snapshot taken before the upload lands has no trace of the row;
        // the engine must not treat absence as deletion.
        engine.processOnce(uid, emptyList())

        assertEquals(SyncState.SYNCED, dao.getById(id, uid)!!.syncState)
        assertEquals(1, repository.observeAll(uid).first().size)
    }

    @Test
    fun localPendingEditWinsOverRemote() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "original")
        val remoteId = dao.getById(id, uid)!!.remoteId!!
        engine.processOnce(uid, remote.observeAll(uid).first())

        // Local edit happens while offline-ish: PENDING, not yet pushed.
        repository.updateContent(id, uid, "local edit")
        // Meanwhile the backend gets an older edit from another device.
        remote.seedRemote(listOf(RemoteCapture(remoteId, "stale remote edit", CaptureType.TEXT, 1000L, 2000L)))

        engine.processOnce(uid, remote.observeAll(uid).first())

        // The local PENDING row must not be clobbered, and the local edit wins.
        assertEquals("local edit", repository.observeAll(uid).first().single().content)
        assertEquals("local edit", remote.uploaded.last().content)
    }

    @Test
    fun deleteTombstoneIsDrainedAfterRemoteDelete() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "delete me")
        val remoteId = dao.getById(id, uid)!!.remoteId!!
        engine.processOnce(uid, remote.observeAll(uid).first())

        repository.delete(id, uid)
        engine.processOnce(uid, remote.observeAll(uid).first())

        assertTrue(remote.deleted.contains(remoteId))
        assertNull(dao.getByIdIncludingDeleted(id))
        // And it must never come back — not in the library, not in search.
        assertTrue(repository.observeAll(uid).first().isEmpty())
    }

    @Test
    fun staleRemoteSnapshotNeverResurrectsALocallyDeletedBlurt() = runTest {
        val id = repository.create(uid, CaptureType.TEXT, "delete me for real")
        val remoteId = dao.getById(id, uid)!!.remoteId!!
        engine.processOnce(uid, remote.observeAll(uid).first())

        // The user deletes locally. The remote snapshot passed to the next
        // pass is stale — it still carries the pre-delete doc, and the remote
        // delete also bounces offline. The blurt must stay deleted.
        repository.delete(id, uid)
        remote.failNextDelete = true
        engine.processOnce(uid, remote.observeAll(uid).first())

        assertTrue(repository.observeAll(uid).first().isEmpty())
        assertNotNull(dao.getByIdIncludingDeleted(id)) // still a tombstone

        // The retry drains the delete; the stale doc still must not
        // resurrect anything.
        remote.failNextDelete = false
        engine.processOnce(uid, remote.observeAll(uid).first())
        assertTrue(remote.deleted.contains(remoteId))
        assertTrue(repository.observeAll(uid).first().isEmpty())
        assertNull(dao.getByIdIncludingDeleted(id))
    }

    @Test
    fun failedUploadStaysPendingAndSucceedsOnRetry() = runTest {
        remote.failNextUpload = true
        val id = repository.create(uid, CaptureType.TEXT, "flaky")

        engine.processOnce(uid, remote.observeAll(uid).first())

        assertEquals(SyncState.PENDING, dao.getById(id, uid)!!.syncState)
        assertTrue(remote.uploaded.isEmpty())

        // Backend recovers; the next pass uploads it.
        remote.failNextUpload = false
        engine.processOnce(uid, remote.observeAll(uid).first())

        assertTrue(remote.uploaded.any { it.content == "flaky" })
        assertEquals(SyncState.SYNCED, dao.getById(id, uid)!!.syncState)
    }
}

/** In-memory stand-in for Firestore+Storage: same contract, deterministic. */
private class FakeCaptureRemote : CaptureRemote {

    override val isConfigured: Boolean = true

    var failNextUpload = false
    var failNextDelete = false

    val uploaded = mutableListOf<RemoteCapture>()
    val deleted = mutableListOf<String>()
    private val docs = MutableStateFlow<List<RemoteCapture>>(emptyList())

    override suspend fun uploadCapture(uid: String, capture: RemoteCapture) {
        if (failNextUpload) {
            failNextUpload = false
            throw RuntimeException("backend unavailable")
        }
        uploaded += capture
        docs.value = docs.value.filterNot { it.remoteId == capture.remoteId } +
            capture.copy(deleted = false)
    }

    override suspend fun deleteCapture(uid: String, remoteId: String) {
        if (failNextDelete) {
            failNextDelete = false
            throw RuntimeException("backend unavailable")
        }
        deleted += remoteId
        // Tombstone, matching the real backend: the doc stays visible as
        // deleted so other devices converge.
        docs.value = docs.value.map {
            if (it.remoteId == remoteId) it.copy(deleted = true) else it
        }
    }

    override fun observeAll(uid: String): Flow<List<RemoteCapture>> = docs

    /** Simulates writes coming from another device. */
    fun seedRemote(captures: List<RemoteCapture>) {
        docs.value = captures
    }
}
