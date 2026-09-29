package com.example.butler.domain.logic

import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.TodoItem
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.TodoEntity

class UpdateTodoCommand(
    override val history: OperationHistory,
    val newTodo: TodoItem,
    val oldTodo: TodoItem,
    private val todoDao: TodoDao?,
    private val inMemoryTodoList: MutableList<TodoItem>? = null
) : Command {

    override suspend fun execute(): Boolean {
        if (todoDao != null) {
            todoDao.updateTodo(TodoEntity.fromDomainModel(newTodo))
        }
        val idx = inMemoryTodoList?.indexOfFirst { it.id == newTodo.id }
        if (idx != null && idx >= 0) {
            inMemoryTodoList[idx] = newTodo
        }
        return true
    }

    override suspend fun undo(): Boolean {
        if (todoDao != null) {
            todoDao.updateTodo(TodoEntity.fromDomainModel(oldTodo))
        }
        val idx = inMemoryTodoList?.indexOfFirst { it.id == oldTodo.id }
        if (idx != null && idx >= 0) {
            inMemoryTodoList[idx] = oldTodo
        }
        return true
    }
}