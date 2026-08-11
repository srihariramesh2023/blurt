package com.blurt.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaptureEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(CaptureTypeConverter::class, SyncStateConverter::class)
abstract class BlurtDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

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
    }
}
