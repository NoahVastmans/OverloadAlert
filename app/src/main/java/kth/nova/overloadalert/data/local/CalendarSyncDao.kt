package kth.nova.overloadalert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for managing calendar synchronization status.
 *
 * This interface defines the database operations for the `calendar_sync` table,
 * allowing the application to track when specific dates were last synchronized
 * with an external calendar provider to prevent redundant network calls or processing.
 */
@Dao
interface CalendarSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(syncEntity: CalendarSyncEntity)

    @Query("SELECT * FROM calendar_sync WHERE date = :date")
    suspend fun getSyncEntity(date: String): CalendarSyncEntity?

    @Query("DELETE FROM calendar_sync WHERE date = :date")
    suspend fun deleteSyncEntity(date: String)

    @Query("DELETE FROM calendar_sync")
    suspend fun clearAll()
}