package com.blurt.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.blurt.app.data.local.BlurtDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test
    fun migrate3To4_dropsImageColumnsAndConvertsImageRowsToText() = runTest {
        v3Schema().use { v3 ->
            BlurtDatabase.MIGRATION_3_4.migrate(v3)
            v3.version = 4
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_3_4)
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
}
