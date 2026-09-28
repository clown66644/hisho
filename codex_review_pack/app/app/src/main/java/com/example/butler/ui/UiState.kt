package com.example.butler.ui

import com.example.butler.data.remote.PersonaType
import com.example.butler.domain.logic.Command

sealed class CardItem {
    abstract val id: String

    data class TodoCardItem(
        override val id: String,
        val title: String,
        val detail: String?,
        val rankTag: String,
        val isPinned: Boolean = false
    ) : CardItem()

    data class ConfirmationCardItem(
        override val id: String,
        val actionType: String,
        val title: String,
        val description: String,
        val pendingCommand: Command
    ) : CardItem()
}

data class MainUiState(
    val cards: List<CardItem> = emptyList(),
    val currentPersona: PersonaType = PersonaType.BUTLER,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null
)
