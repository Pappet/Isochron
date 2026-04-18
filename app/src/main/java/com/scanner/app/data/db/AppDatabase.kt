package com.scanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DiscoveredDeviceEntity::class,
        ScanSessionEntity::class,
        SignalReadingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Add new migrations here whenever the schema version is bumped.
         * Example:
         *   val MIGRATION_1_2 = object : Migration(1, 2) {
         *       override fun migrate(db: SupportSQLiteDatabase) {
         *           db.execSQL("ALTER TABLE discovered_devices ADD COLUMN new_column TEXT")
         *       }
         *   }
         */

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scanner_db"
                )
                    // DO NOT add fallbackToDestructiveMigration() — all schema changes
                    // must be handled via explicit Migration objects listed above to
                    // prevent data loss for the user.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
