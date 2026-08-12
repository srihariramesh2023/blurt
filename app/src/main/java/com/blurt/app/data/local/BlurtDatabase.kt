package com.blurt.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaptureEntity::class, EmbeddingEntity::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(
    CaptureTypeConverter::class,
    SyncStateConverter::class,
    EmbeddingConverter::class,
)
abstract class BlurtDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun embeddingDao(): EmbeddingDao

    companion object {
        /**
         * v1 → v2: adds per-user ownership so captures are scoped to the
         * authenticated user. Existing rows start unowned (NULL) and are
         * claimed by the first user who signs in on the device.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN ownerId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
            }
        }

        /**
         * v2 → v3: adds cross-device sync state. Existing rows default to
         * PENDING so everything already saved uploads on the next sync.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE captures ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE captures ADD COLUMN imageUrl TEXT")
                db.execSQL("ALTER TABLE captures ADD COLUMN deletedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
            }
        }

        /**
         * v3 → v4: removes the image feature. The image columns are dropped and
         * legacy IMAGE rows become TEXT so no data is lost and nothing crashes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS captures_new (" +
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
                db.execSQL(
                    "INSERT INTO captures_new (id, ownerId, remoteId, syncState, content, type, deletedAt, createdAt, updatedAt) " +
                        "SELECT id, ownerId, remoteId, syncState, content, " +
                        "CASE WHEN type = 'IMAGE' THEN 'TEXT' ELSE type END, " +
                        "deletedAt, createdAt, updatedAt FROM captures"
                )
                db.execSQL("DROP TABLE captures")
                db.execSQL("ALTER TABLE captures_new RENAME TO captures")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_ownerId ON captures(ownerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_syncState ON captures(syncState)")
            }
        }

        /**
         * v4 → v5: adds the local semantic-search cache. Each capture's
         * Gemini embedding lives in its own table so blurts are embedded once
         * and reused; vectors are scoped by owner like everything else.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `capture_embeddings` (" +
                        "`captureId` INTEGER NOT NULL PRIMARY KEY, " +
                        "`ownerId` TEXT NOT NULL, " +
                        "`embedding` BLOB NOT NULL, " +
                        "`model` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_capture_embeddings_ownerId " +
                        "ON capture_embeddings(ownerId)"
                )
            }
        }

        /**
         * v5 → v6: adds the AI capture analysis. `category` stores the fixed
         * CaptureCategory enum name (null = not yet classified); `reminderAt`
         * is when a priority notification was scheduled for. Existing rows
         * default to NULL — they are backfilled lazily by the analyzer.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE captures ADD COLUMN reminderAt INTEGER")
            }
        }
    }
}
