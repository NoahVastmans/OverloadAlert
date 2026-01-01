package kth.nova.overloadalert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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