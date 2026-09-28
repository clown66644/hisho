package com.example.butler.ui

import com.example.butler.domain.model.TodoItem

data class MainUiState(
    val todos: List<TodoItem> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String = "準備中です。",
)
