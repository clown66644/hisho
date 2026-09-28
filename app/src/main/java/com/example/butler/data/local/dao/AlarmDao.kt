package com.example.butler.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.butler.data.local.entity.AlarmItemEntity

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY triggerAtMillis ASC")
    suspend fun getAllAlarms(): List<AlarmItemEntity>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getAlarmById(id: String): AlarmItemEntity?

    @Query("SELECT * FROM alarms WHERE isFired = 0 AND isCancelled = 0 AND triggerAtMillis > :currentTime ORDER BY triggerAtMillis ASC")
    suspend fun getPendingFutureAlarms(currentTime: Long): List<AlarmItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmItemEntity)

    @Update
    suspend fun updateAlarm(alarm: AlarmItemEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmItemEntity)
}
