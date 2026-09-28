package com.example.butler.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.butler.data.local.entity.OperationHistoryEntity

@Dao
interface OperationHistoryDao {
    @Query("SELECT * FROM operation_histories ORDER BY operationOrder ASC")
    suspend fun getAllHistories(): List<OperationHistoryEntity>

    @Query("SELECT * FROM operation_histories WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: String): OperationHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(history: OperationHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: OperationHistoryEntity): Int

    @Query("SELECT COALESCE(MAX(operationOrder), 0) + 1 FROM operation_histories")
    suspend fun getNextOperationOrder(): Long

    @Query(
        """
        UPDATE operation_histories
        SET isRedoable = 0, updatedAt = :updatedAt
        WHERE isUndone = 1 AND isRedoable = 1
        """
    )
    suspend fun invalidateRedoHistories(updatedAt: Long): Int
}
