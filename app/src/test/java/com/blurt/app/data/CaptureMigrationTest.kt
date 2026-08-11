package com.blurt.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.blurt.app.data.local.BlurtDatabase
import com.blurt.app.data.model.CaptureType
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

    @Test
    fun migrate1To3_keepsLegacyRowsAndRoomValidates() = runTest {
        v1Schema().use { v1 ->
            BlurtDatabase.MIGRATION_1_2.migrate(v1)
            BlurtDatabase.MIGRATION_2_3.migrate(v1)
            v1.version = 3
        }

        // Room re-opens at v3 — throws on any schema mismatch.
        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_1_2, BlurtDatabase.MIGRATION_2_3)
            .build()
        val repository = CaptureRepository(database.captureDao(), ImageStore(context))

        // Legacy row survived, unowned, and is now PENDING for the first sync.
        assertTrue(repository.observeAll("any-user").first().isEmpty())
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT ownerId, syncState, remoteId, deletedAt FROM captures ORDER BY createdAt",
        )
        cursor.moveToFirst()
        assertNull(cursor.getString(0)) // ownerId
        assertEquals("PENDING", cursor.getString(1)) // syncState default
        assertNull(cursor.getString(2)) // remoteId
        assertNull(cursor.getString(3)) // deletedAt
        cursor.close()

        repository.claimUnowned("uid-first-user")
        val claimed = repository.observeAll("uid-first-user").first()
        assertEquals(1, claimed.size)
        assertEquals("legacy note from before auth", claimed[0].content)

        database.close()
    }

    @Test
    fun migrate2To3_addsSyncColumnsWithPendingDefault() = runTest {
        v2Schema().use { v2 ->
            BlurtDatabase.MIGRATION_2_3.migrate(v2)
            v2.version = 3
        }

        val database = Room.databaseBuilder(context, BlurtDatabase::class.java, dbName)
            .addMigrations(BlurtDatabase.MIGRATION_1_2, BlurtDatabase.MIGRATION_2_3)
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
}
