package com.example.butler.ui

import com.example.butler.data.remote.PersonaType
import com.example.butler.domain.logic.AiCommandConverter
import com.example.butler.domain.logic.ChangePersonaCommand
import com.example.butler.domain.logic.Command
import com.example.butler.domain.logic.CreateTodoCommand
import com.example.butler.domain.logic.ForbiddenOperationException
import com.example.butler.domain.logic.OperationPolicyManager
import com.example.butler.domain.logic.PolicyLevel
import com.example.butler.domain.logic.PriorityCalculator
import com.example.butler.domain.logic.UndoManager
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.PriorityLevel
import com.example.butler.domain.model.TodoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    val undoManager: UndoManager = UndoManager(),
    private val converter: AiCommandConverter = AiCommandConverter()
) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val currentTodoList = mutableListOf<TodoItem>()
    private val calculator = PriorityCalculator()
    private val processingCardIds = mutableSetOf<String>()

    fun changePersona(newPersona: PersonaType) {
        CoroutineScope(Dispatchers.Main).launch {
            changePersonaSuspend(newPersona)
        }
    }

    suspend fun changePersonaSuspend(newPersona: PersonaType): Boolean {
        val prev = _uiState.value.currentPersona
        if (prev == newPersona) return false

        val history = OperationHistory(
            id = UUID.randomUUID().toString(),
            actor = Actor.USER,
            actionType = "CHANGE_PERSONA",
            targetId = newPersona.name
        )

        val command = ChangePersonaCommand(
            history = history,
            newPersona = newPersona,
            previousPersona = prev,
            onPersonaChanged = { targetPersona ->
                _uiState.value = _uiState.value.copy(
                    currentPersona = targetPersona,
                    statusMessage = "ペルソナを「${getPersonaDisplayName(targetPersona)}」に変更しました。"
                )
            }
        )

        return executeCommandInternal(command)
    }

    private fun getPersonaDisplayName(persona: PersonaType): String {
        return when (persona) {
            PersonaType.BUTLER -> "AI執事"
            PersonaType.SECRETARY -> "AI秘書"
            PersonaType.COACH -> "AIコーチ"
            PersonaType.MAID -> "AIメイド"
        }
    }

    fun handleAiJsonInput(jsonString: String) {
        CoroutineScope(Dispatchers.Main).launch {
            handleAiJsonInputSuspend(jsonString)
        }
    }

    suspend fun handleAiJsonInputSuspend(jsonString: String) {
        try {
            val command = converter.convertJsonToCommand(jsonString, Actor.AI_BUTLER)
            val actionType = command.history.actionType
            val policy = OperationPolicyManager.evaluatePolicy(actionType)

            when (policy) {
                PolicyLevel.AUTO_EXECUTABLE -> {
                    executeCommandInternal(command)
                }
                PolicyLevel.CONFIRMATION_REQUIRED -> {
                    val confirmCard = CardItem.ConfirmationCardItem(
                        id = UUID.randomUUID().toString(),
                        actionType = actionType,
                        title = "確認要請: $actionType",
                        description = "この操作 (${command.history.id}) の実行を承認しますか？",
                        pendingCommand = command
                    )
                    _uiState.value = _uiState.value.copy(
                        cards = listOf<CardItem>(confirmCard) + _uiState.value.cards,
                        statusMessage = "確認カードを提示しました。"
                    )
                }
                PolicyLevel.FORBIDDEN -> {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "禁止された操作です。"
                    )
                }
            }
        } catch (e: ForbiddenOperationException) {
            _uiState.value = _uiState.value.copy(statusMessage = e.message)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(statusMessage = "エラー: ${e.message}")
        }
    }

    suspend fun executeCommandInternal(command: Command): Boolean {
        val success = undoManager.executeCommand(command)
        if (success) {
            if (command is CreateTodoCommand) {
                currentTodoList.add(command.todo)
                refreshCards()
            }
        }
        updateUndoRedoStatus()
        return success
    }

    suspend fun approveConfirmationCard(cardId: String): Boolean {
        if (processingCardIds.contains(cardId)) return false
        processingCardIds.add(cardId)

        try {
            val card = _uiState.value.cards.filterIsInstance<CardItem.ConfirmationCardItem>()
                .find { it.id == cardId } ?: return false

            val success = executeCommandInternal(card.pendingCommand)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    cards = _uiState.value.cards.filter { it.id != cardId },
                    statusMessage = "操作を承認・実行しました。"
                )
            }
            return success
        } finally {
            processingCardIds.remove(cardId)
        }
    }

    fun rejectConfirmationCard(cardId: String) {
        if (processingCardIds.contains(cardId)) return
        processingCardIds.add(cardId)

        try {
            _uiState.value = _uiState.value.copy(
                cards = _uiState.value.cards.filter { it.id != cardId },
                statusMessage = "操作提案を拒否しました。"
            )
        } finally {
            processingCardIds.remove(cardId)
        }
    }

    suspend fun undo(): Boolean {
        val success = undoManager.undoLastCommand()
        if (success) {
            if (currentTodoList.isNotEmpty()) {
                currentTodoList.removeAt(currentTodoList.size - 1)
            }
            refreshCards()
        }
        updateUndoRedoStatus()
        return success
    }

    suspend fun redo(): Boolean {
        val success = undoManager.redoNextCommand()
        if (success) {
            refreshCards()
        }
        updateUndoRedoStatus()
        return success
    }

    private fun refreshCards() {
        val sortedTodos = currentTodoList.sortedByDescending { calculator.calculatePriority(it).first }
        val todoCards: List<CardItem> = sortedTodos.map { todo ->
            val level = calculator.calculatePriority(todo).second
            val rankTag = when (level) {
                PriorityLevel.TOP_PRIORITY -> "S"
                PriorityLevel.HIGH -> "A"
                PriorityLevel.MEDIUM -> "B"
                PriorityLevel.LOW -> "C"
            }
            CardItem.TodoCardItem(
                id = todo.id,
                title = todo.title,
                detail = todo.detail,
                rankTag = rankTag,
                isPinned = todo.isPinnedPriority
            )
        }

        val confirmationCards: List<CardItem> = _uiState.value.cards.filterIsInstance<CardItem.ConfirmationCardItem>()
        _uiState.value = _uiState.value.copy(
            cards = confirmationCards + todoCards
        )
    }

    private fun updateUndoRedoStatus() {
        _uiState.value = _uiState.value.copy(
            canUndo = undoManager.canUndo(),
            canRedo = undoManager.canRedo()
        )
    }
}
