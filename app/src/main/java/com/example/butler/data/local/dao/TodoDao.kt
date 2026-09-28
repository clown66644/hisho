package com.example.butler.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.butler.data.local.entity.TodoEntity

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    suspend fun getAllTodos(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodoById(id: String): TodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoEntity)

    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Delete
    suspend fun deleteTodo(todo: TodoEntity)
}
