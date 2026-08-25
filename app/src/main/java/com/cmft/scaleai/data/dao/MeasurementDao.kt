package com.cmft.scaleai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cmft.scaleai.data.entity.Measurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: Measurement): Long

    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeAll(userId: Long): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(userId: Long): Measurement?

    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(userId: Long): Flow<Measurement?>

    @Query("SELECT * FROM measurements WHERE userId = :userId AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getBetween(userId: Long, start: Long, end: Long): List<Measurement>

    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC LIMIT :count")
    suspend fun getRecent(userId: Long, count: Int): List<Measurement>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): Measurement?

    @Query("UPDATE measurements SET reportGenerated = :generated WHERE id = :id")
    suspend fun setReportGenerated(id: Long, generated: Boolean)
}
