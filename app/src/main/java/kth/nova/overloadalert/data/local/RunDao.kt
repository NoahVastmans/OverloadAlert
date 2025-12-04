package kth.nova.overloadalert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runs: List<Run>)

    @Query("SELECT * FROM runs WHERE startDate BETWEEN :startDate AND :endDate ORDER BY startDate DESC")
    fun getRuns(startDate: OffsetDateTime, endDate: OffsetDateTime): Flow<List<Run>>
}