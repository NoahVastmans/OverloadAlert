package kth.nova.overloadalert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runs: List<Run>)

    @Query("SELECT * FROM runs WHERE startDateLocal >= :sinceDate ORDER BY startDateLocal DESC")
    suspend fun getRunsSince(sinceDate: String): List<Run>

    @Query("DELETE FROM runs")
    suspend fun clearAll()
}