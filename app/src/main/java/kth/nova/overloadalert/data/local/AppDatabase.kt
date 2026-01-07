package kth.nova.overloadalert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The main Room database for the application.
 *
 * This abstract class serves as the main access point for the underlying connection to the
 * application's local SQLite data. It defines the database configuration and serves as the
 * primary source for Data Access Objects (DAOs).
 *
 * Included Entities:
 * - [Run]: Stores data related to user runs.
 * - [CalendarSyncEntity]: Stores synchronization state for calendar events.
 *
 * Database Version: 2
 *
 * @see RunDao
 * @see CalendarSyncDao
 */
@Database(entities = [Run::class, CalendarSyncEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
    abstract fun calendarSyncDao(): CalendarSyncDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_sync` (" +
                            "`date` TEXT NOT NULL, " +
                            "`googleEventId` TEXT NOT NULL, " +
                            "`lastSyncedAt` INTEGER NOT NULL, " +
                            "`userModifiedTime` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`date`))"
                )
            }
        }
    }
}