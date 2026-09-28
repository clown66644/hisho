package com.example.butler.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.butler.data.local.entity.OperationHistoryEntity

@Dao
interface OperationHistoryDao {
    @Query("SELECT * FROM operation_histories ORDER BY timestamp ASC")
    suspend fun getAllHistories(): List<OperationHistoryEntity>

    @Query("SELECT * FROM operation_histories WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: String): OperationHistoryEntity?

    @Query("SELECT * FROM operation_histories WHERE isUndone = 0 AND status = 'SUCCESS' ORDER BY timestamp ASC")
    suspend fun getUndoableHistories(): List<OperationHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(history: OperationHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: OperationHistoryEntity)

    @Query("DELETE FROM operation_histories WHERE id = :id")
    suspend fun deleteHistoryById(id: String)
}
