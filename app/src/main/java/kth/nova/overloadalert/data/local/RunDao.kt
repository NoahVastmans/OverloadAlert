package kth.nova.overloadalert.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runs: List<Run>)

    @Query("SELECT * FROM runs WHERE startDateLocal >= :sinceDate ORDER BY startDateLocal DESC")
    fun getRunsSince(sinceDate: String): Flow<List<Run>>

    @Query("SELECT * FROM runs ORDER BY startDateLocal DESC")
    fun getAllRuns(): Flow<List<Run>>

    @Delete
    suspend fun deleteRuns(runs: List<Run>)

    @Query("DELETE FROM runs")
    suspend fun clearAll()
}