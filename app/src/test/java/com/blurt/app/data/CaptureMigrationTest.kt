package com.blurt.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.local.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exercises the exact migrations a real install runs when its existing
 * database is first opened by the new app. Each test rebuilds the schema the
 * app wrote at that version, runs the production migrations (mimicking Room:
 * run the migration, then stamp the new version), and re-validates with Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureMigrationTest {

    private lateinit var context: Context
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    private fun createDatabaseAt(
        version: Int,
        createSchema: SupportSQLiteDatabase.() -> Unit,
    ): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createSchema(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        return helper.writableDatabase
    }

    private fun v1Schema() = createDatabaseAt(1) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`imageUri` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL(
            "INSERT INTO `captures` (content, type, imageUri, createdAt, updatedAt) " +
                "VALUES ('legacy note from before auth', 'TEXT', NULL, 1000, 1000)"
        )
        execSQL(
            "INSERT INTO `captures` (content, type, imageUri, createdAt, updatedAt) " +
                "VALUES ('legacy image note', 'IMAGE', 'file:///data/old.jpg', 1001, 1001)"
        )
    }

    private fun v2Schema() = createDatabaseAt(2) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`imageUri` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL(
            "INSERT INTO `captures` (ownerId, content, type, imageUri, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'owned note', 'TEXT', NULL, 1000, 1000)"
        )
    }

    private fun v3Schema() = createDatabaseAt(3) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`imageUri` TEXT, " +
                "`imageUrl` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, imageUri, imageUrl, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'kept note', 'TEXT', NULL, NULL, 1000, 1000)"
        )
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, imageUri, imageUrl, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-2', 'SYNCED', 'old image caption', 'IMAGE', 'file:///data/img.jpg', 'https://x/y.jpg', 1001, 1001)"
        )
    }

    private fun columnNames(db: SupportSQLiteDatabase): Set<String> {
        val cursor = db.query("PRAGMA table_info(captures)")
        val names = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        cursor.close()
        return names
    }

    @Test
    fun migrate1To4_keepsLegacyRowsConvertsImagesAndRoomValidates() = runTest {
        v1Schema().use { v1 ->
            BlurtDatabase.MIGRATION_1_2.migrate(v1)
            BlurtDatabase.MIGRATION_2_3.migrate(v1)
            BlurtDatabase.MIGRATION_3_4.migrate(v1)
            v1.version = 4
        }

        // Room re-opens at v4 — throws on any schema mismatch.
        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(
                BlurtDatabase.MIGRATION_1_2,
                BlurtDatabase.MIGRATION_2_3,
                BlurtDatabase.MIGRATION_3_4,
                BlurtDatabase.MIGRATION_4_5,
                BlurtDatabase.MIGRATION_5_6,
                BlurtDatabase.MIGRATION_6_7,
                BlurtDatabase.MIGRATION_7_8,
                BlurtDatabase.MIGRATION_8_9,
            )
            .build()
        val repository = CaptureRepository(database.captureDao())

        // Legacy rows survived, unowned, and are now PENDING for the first sync.
        assertTrue(repository.observeAll("any-user").first().isEmpty())
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT ownerId, syncState, remoteId, deletedAt, type, content FROM captures ORDER BY createdAt",
        )
        cursor.moveToFirst()
        assertNull(cursor.getString(0)) // ownerId
        assertEquals("PENDING", cursor.getString(1)) // syncState default
        assertNull(cursor.getString(2)) // remoteId
        assertNull(cursor.getString(3)) // deletedAt
        assertEquals("TEXT", cursor.getString(4)) // legacy IMAGE row → TEXT
        assertEquals("legacy note from before auth", cursor.getString(5))
        cursor.moveToNext()
        assertEquals("TEXT", cursor.getString(4)) // the IMAGE row converted to TEXT
        assertEquals("legacy image note", cursor.getString(5))
        cursor.close()

        // The image columns are gone after v4.
        val columns = columnNames(database.openHelper.writableDatabase)
        assertTrue("imageUri should be dropped", "imageUri" !in columns)
        assertTrue("imageUrl should be dropped", "imageUrl" !in columns)

        repository.claimUnowned("uid-first-user")
        val claimed = repository.observeAll("uid-first-user").first()
        assertEquals(2, claimed.size)
        assertTrue(claimed.all { it.type.name == "TEXT" })

        database.close()
    }

    @Test
    fun migrate2To4_addsSyncColumnsWithPendingDefault() = runTest {
        v2Schema().use { v2 ->
            BlurtDatabase.MIGRATION_2_3.migrate(v2)
            BlurtDatabase.MIGRATION_3_4.migrate(v2)
            v2.version = 4
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(
                BlurtDatabase.MIGRATION_1_2,
                BlurtDatabase.MIGRATION_2_3,
                BlurtDatabase.MIGRATION_3_4,
                BlurtDatabase.MIGRATION_4_5,
                BlurtDatabase.MIGRATION_5_6,
                BlurtDatabase.MIGRATION_6_7,
                BlurtDatabase.MIGRATION_7_8,
                BlurtDatabase.MIGRATION_8_9,
            )
            .build()

        // Existing owned row is preserved and queued for upload.
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT ownerId, syncState, content FROM captures",
        )
        cursor.moveToFirst()
        assertEquals("uid-existing-user", cursor.getString(0))
        assertEquals("PENDING", cursor.getString(1))
        assertEquals("owned note", cursor.getString(2))
        cursor.close()

        // The sync index exists for the upload queue query.
        val index = database.openHelper.writableDatabase.query("PRAGMA index_list(captures)")
        var found = false
        while (index.moveToNext()) {
            val name = index.getString(index.getColumnIndexOrThrow("name"))
            if (name == "index_captures_syncState") found = true
        }
        index.close()
        database.close()
        assertTrue("syncState index missing after migration", found)
    }

    private fun v4Schema() = createDatabaseAt(4) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'kept note', 'TEXT', 1000, 1000)"
        )
    }

    @Test
    fun migrate4To5_addsEmbeddingsTableKeepsCaptures() = runTest {
        v4Schema().use { v4 ->
            BlurtDatabase.MIGRATION_4_5.migrate(v4)
            v4.version = 5
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(
                BlurtDatabase.MIGRATION_4_5,
                BlurtDatabase.MIGRATION_5_6,
                BlurtDatabase.MIGRATION_6_7,
                BlurtDatabase.MIGRATION_7_8,
                BlurtDatabase.MIGRATION_8_9,
            )
            .build()

        // Room validates the new schema — the embeddings table must exist with
        // the exact columns Room expects.
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT captureId, ownerId, model FROM capture_embeddings",
        )
        assertTrue(cursor.count == 0) // empty cache, but the table exists
        cursor.close()

        // Existing captures are untouched.
        val repo = CaptureRepository(database.captureDao())
        val all = repo.observeAll("uid-existing-user").first()
        assertEquals(1, all.size)
        assertEquals("kept note", all.single().content)
        assertEquals("remote-1", all.single().remoteId)

        database.close()
    }

    private fun v5Schema() = createDatabaseAt(5) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "CREATE TABLE IF NOT EXISTS `capture_embeddings` (" +
                "`captureId` INTEGER NOT NULL PRIMARY KEY, " +
                "`ownerId` TEXT NOT NULL, " +
                "`embedding` BLOB NOT NULL, " +
                "`model` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'kept note', 'TEXT', 1000, 1000)"
        )
    }

    @Test
    fun migrate5To6_addsCategoryAndReminderColumnsKeepsRows() = runTest {
        v5Schema().use { v5 ->
            BlurtDatabase.MIGRATION_5_6.migrate(v5)
            v5.version = 6
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(
                BlurtDatabase.MIGRATION_5_6,
                BlurtDatabase.MIGRATION_6_7,
                BlurtDatabase.MIGRATION_7_8,
                BlurtDatabase.MIGRATION_8_9,
            )
            .build()

        // Existing rows survive, uncategorized and without reminders — the
        // analyzer backfills them lazily.
        val repo = CaptureRepository(database.captureDao())
        val all = repo.observeAll("uid-existing-user").first()
        assertEquals(1, all.size)
        assertNull(all.single().category)
        assertNull(all.single().reminderAt)
        assertEquals("kept note", all.single().content)

        // The new columns are queryable — a category can be written and read back.
        val dao = database.captureDao()
        val entity = dao.getById(all.single().id, "uid-existing-user")!!
        dao.update(entity.copy(category = "TRAVEL", reminderAt = 1234L))
        val updated = repo.observeAll("uid-existing-user").first().single()
        assertEquals(com.blurt.app.data.model.CaptureCategory.TRAVEL, updated.category)
        assertEquals(1234L, updated.reminderAt?.toEpochMilli())

        database.close()
    }

    @Test
    fun migrate3To4_dropsImageColumnsAndConvertsImageRowsToText() = runTest {
        v3Schema().use { v3 ->
            BlurtDatabase.MIGRATION_3_4.migrate(v3)
            v3.version = 4
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(
                BlurtDatabase.MIGRATION_3_4,
                BlurtDatabase.MIGRATION_4_5,
                BlurtDatabase.MIGRATION_5_6,
                BlurtDatabase.MIGRATION_6_7,
                BlurtDatabase.MIGRATION_7_8,
                BlurtDatabase.MIGRATION_8_9,
            )
            .build()

        val columns = columnNames(database.openHelper.writableDatabase)
        assertTrue("imageUri should be dropped", "imageUri" !in columns)
        assertTrue("imageUrl should be dropped", "imageUrl" !in columns)
        // Owner index survives the table rebuild.
        val indices = database.openHelper.writableDatabase.query("PRAGMA index_list(captures)")
        val names = mutableSetOf<String>()
        while (indices.moveToNext()) {
            names += indices.getString(indices.getColumnIndexOrThrow("name"))
        }
        indices.close()
        assertTrue("index_captures_ownerId", "index_captures_ownerId" in names)
        assertTrue("index_captures_syncState", "index_captures_syncState" in names)

        // The IMAGE row became TEXT with its caption kept; the TEXT row is intact.
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT type, content, syncState, remoteId FROM captures ORDER BY createdAt",
        )
        cursor.moveToFirst()
        assertEquals("TEXT", cursor.getString(0))
        assertEquals("kept note", cursor.getString(1))
        assertEquals("SYNCED", cursor.getString(2))
        assertEquals("remote-1", cursor.getString(3))
        cursor.moveToNext()
        assertEquals("TEXT", cursor.getString(0))
        assertEquals("old image caption", cursor.getString(1))
        assertEquals("SYNCED", cursor.getString(2))
        assertEquals("remote-2", cursor.getString(3))
        cursor.close()

        database.close()
    }

    private fun v6Schema() = createDatabaseAt(6) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`category` TEXT, " +
                "`reminderAt` INTEGER, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "CREATE TABLE IF NOT EXISTS `capture_embeddings` (" +
                "`captureId` INTEGER NOT NULL PRIMARY KEY, " +
                "`ownerId` TEXT NOT NULL, " +
                "`embedding` BLOB NOT NULL, " +
                "`model` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, category, reminderAt, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'kept note', 'TEXT', 'WORK', 5000, 1000, 1000)"
        )
    }

    @Test
    fun migrate6To7_addsIntentAndFlagsKeepsRows() = runTest {
        v6Schema().use { v6 ->
            BlurtDatabase.MIGRATION_6_7.migrate(v6)
            v6.version = 7
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_6_7, BlurtDatabase.MIGRATION_7_8, BlurtDatabase.MIGRATION_8_9)
            .build()

        // Existing rows survive with their analysis, defaulting to unset flags.
        val repo = CaptureRepository(database.captureDao())
        val all = repo.observeAll("uid-existing-user").first()
        assertEquals(1, all.size)
        val capture = all.single()
        assertEquals("kept note", capture.content)
        assertEquals(com.blurt.app.data.model.CaptureCategory.WORK, capture.category)
        assertNull(capture.intent)
        assertEquals(5000L, capture.reminderAt?.toEpochMilli())
        assertFalse(capture.isImportant)
        assertFalse(capture.isArchived)

        // The new columns are queryable — flags can be written and read back.
        val dao = database.captureDao()
        dao.setImportant(capture.id, "uid-existing-user", true, 2000L)
        dao.setArchived(capture.id, "uid-existing-user", true, 2001L)
        val updated = repo.observeAll("uid-existing-user").first()
        assertTrue(updated.isEmpty()) // archived rows leave the main list
        val archived = repo.observeArchived("uid-existing-user").first()
        assertEquals(1, archived.size)
        assertTrue(archived.single().isImportant)
        assertTrue(archived.single().isArchived)

        database.close()
    }

    private fun v7Schema() = createDatabaseAt(7) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`category` TEXT, " +
                "`intent` TEXT, " +
                "`reminderAt` INTEGER, " +
                "`isImportant` INTEGER NOT NULL DEFAULT 0, " +
                "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "CREATE TABLE IF NOT EXISTS `capture_embeddings` (" +
                "`captureId` INTEGER NOT NULL PRIMARY KEY, " +
                "`ownerId` TEXT NOT NULL, " +
                "`embedding` BLOB NOT NULL, " +
                "`model` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, category, intent, reminderAt, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'reminder note', 'TEXT', 'HEALTH', 'REMINDER', 5000, 1000, 1000)"
        )
    }

    @Test
    fun migrate7To8_addsCompletedAtKeepsRows() = runTest {
        v7Schema().use { v7 ->
            BlurtDatabase.MIGRATION_7_8.migrate(v7)
            v7.version = 8
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_7_8, BlurtDatabase.MIGRATION_8_9)
            .build()

        // Existing rows survive, not done, with all v7 analysis intact.
        val repo = CaptureRepository(database.captureDao())
        val all = repo.observeAll("uid-existing-user").first()
        assertEquals(1, all.size)
        val capture = all.single()
        assertEquals("reminder note", capture.content)
        assertEquals(com.blurt.app.data.model.CaptureCategory.HEALTH, capture.category)
        assertEquals(com.blurt.app.data.model.CaptureIntent.REMINDER, capture.intent)
        assertEquals(5000L, capture.reminderAt?.toEpochMilli())
        assertNull(capture.completedAt)

        // Done can be written, read back, and reverted; upcoming-reminders
        // excludes it while done.
        val dao = database.captureDao()
        dao.setCompletedAt(capture.id, "uid-existing-user", 9000L, 2000L)
        val done = repo.observeById(capture.id, "uid-existing-user").first()!!
        assertEquals(9000L, done.completedAt?.toEpochMilli())
        assertTrue(dao.getUpcomingReminders("uid-existing-user", 0L).isEmpty())
        dao.setCompletedAt(capture.id, "uid-existing-user", null, 2001L)
        assertNull(repo.observeById(capture.id, "uid-existing-user").first()!!.completedAt)
        assertEquals(1, dao.getUpcomingReminders("uid-existing-user", 0L).size)

        database.close()
    }

    private fun v8Schema() = createDatabaseAt(8) {
        execSQL(
            "CREATE TABLE IF NOT EXISTS `captures` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncState` TEXT NOT NULL DEFAULT 'PENDING', " +
                "`content` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`category` TEXT, " +
                "`intent` TEXT, " +
                "`reminderAt` INTEGER, " +
                "`isImportant` INTEGER NOT NULL DEFAULT 0, " +
                "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                "`completedAt` INTEGER, " +
                "`deletedAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
        execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
        execSQL(
            "CREATE TABLE IF NOT EXISTS `capture_embeddings` (" +
                "`captureId` INTEGER NOT NULL PRIMARY KEY, " +
                "`ownerId` TEXT NOT NULL, " +
                "`embedding` BLOB NOT NULL, " +
                "`model` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        execSQL(
            "INSERT INTO `captures` (ownerId, remoteId, syncState, content, type, category, intent, reminderAt, isImportant, isArchived, completedAt, createdAt, updatedAt) " +
                "VALUES ('uid-existing-user', 'remote-1', 'SYNCED', 'one-shot reminder', 'TEXT', 'HEALTH', 'REMINDER', 5000, 0, 0, NULL, 1000, 1000)"
        )
    }

    @Test
    fun migrate8To9_addsRecurrenceColumnKeepsRows() = runTest {
        v8Schema().use { v8 ->
            BlurtDatabase.MIGRATION_8_9.migrate(v8)
            v8.version = 9
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_8_9)
            .build()

        // Existing rows survive; one-shots read back as NONE recurrence.
        val repo = CaptureRepository(database.captureDao())
        val all = repo.observeAll("uid-existing-user").first()
        assertEquals(1, all.size)
        val capture = all.single()
        assertEquals("one-shot reminder", capture.content)
        assertEquals(com.blurt.app.data.model.CaptureIntent.REMINDER, capture.intent)
        assertEquals(com.blurt.app.data.model.Recurrence.NONE, capture.recurrence)

        // The new column is queryable — a recurrence can be written and read
        // back, and the recurring-reminders query finds it.
        val dao = database.captureDao()
        dao.update(capture.toEntity().copy(recurrence = "DAILY"))
        val updated = repo.observeById(capture.id, "uid-existing-user").first()!!
        assertEquals(com.blurt.app.data.model.Recurrence.DAILY, updated.recurrence)
        assertEquals(1, dao.getRecurringReminders("uid-existing-user").size)
        assertEquals(0, dao.getRecurringReminders("other-user").size)

        database.close()
    }
}
