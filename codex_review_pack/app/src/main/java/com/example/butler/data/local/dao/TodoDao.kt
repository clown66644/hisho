package com.example.butler.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.butler.data.local.entity.TodoEntity

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    suspend fun getAllTodos(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodoById(id: String): TodoEntity?

    @Upsert
    suspend fun upsertTodo(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTodo(todo: TodoEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoEntity): Int

    @Delete
    suspend fun deleteTodo(todo: TodoEntity): Int
}
