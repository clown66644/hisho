package com.example.butler.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.CalendarSyncManager
import com.example.butler.data.local.dao.TodoDao
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
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(
    val undoManager: UndoManager = UndoManager(),
    private val todoDao: TodoDao? = null,
    private val calendarSyncManager: CalendarSyncManager? = null,
    private val converter: AiCommandConverter = AiCommandConverter(
        todoDao = todoDao,
        calendarSyncManager = calendarSyncManager
    ),
    val isDatabaseAvailable: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val currentTodoList = mutableListOf<TodoItem>()
    private val calculator = PriorityCalculator()
    private val processingCardIds = mutableSetOf<String>()

    init {
        if (!isDatabaseAvailable) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "安全なデータ保存領域を開けませんでした。データ保護のため編集機能を停止しています。"
            )
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                undoManager.initialize()
                loadTodosInternal()
            }
        }
    }

    fun loadTodos() {
        if (isDatabaseAvailable && todoDao != null) {
            CoroutineScope(Dispatchers.IO).launch {
                loadTodosInternal()
            }
        }
    }

    private suspend fun loadTodosInternal() {
        if (todoDao != null) {
            val list = todoDao.getAllTodos().map { it.toDomainModel() }
            withContext(Dispatchers.Main) {
                currentTodoList.clear()
                currentTodoList.addAll(list)
                refreshCards()
            }
        }
    }

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
        if (!isDatabaseAvailable) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "安全なデータ保存領域を開けませんでした。データ保護のため編集機能を停止しています。"
            )
            return false
        }
        val success = undoManager.executeCommand(command)
        if (success) {
            if (command is CreateTodoCommand) {
                if (todoDao != null) {
                    loadTodos()
                } else {
                    currentTodoList.add(command.todo)
                    refreshCards()
                }
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
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "操作の実行に失敗しました。必要な情報が不足しているか無効です。"
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
        val lastCommand = undoManager.peekUndoCommand()
        val success = undoManager.undoLastCommand()
        if (success) {
            if (todoDao != null) {
                loadTodos()
            } else {
                // DB無しのインメモリ環境（テスト等）では、直前操作がToDo作成だった場合のみリストから取り除く
                if (lastCommand is CreateTodoCommand) {
                    currentTodoList.removeIf { it.id == lastCommand.todo.id }
                }
                refreshCards()
            }
        }
        updateUndoRedoStatus()
        return success
    }

    suspend fun redo(): Boolean {
        val success = undoManager.redoNextCommand()
        if (success) {
            if (todoDao != null) {
                loadTodos()
            } else {
                refreshCards()
            }
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

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = try {
                AppDatabase.getInstance(context)
            } catch (e: Exception) {
                null
            }
            val todoDao = db?.todoDao()
            val historyDao = db?.operationHistoryDao()
            val undoManager = UndoManager(historyDao = historyDao)
            val calendarSyncManager = CalendarSyncManager(context.applicationContext)
            val converter = AiCommandConverter(
                todoDao = todoDao,
                calendarSyncManager = calendarSyncManager
            )
            return MainViewModel(
                undoManager = undoManager,
                todoDao = todoDao,
                calendarSyncManager = calendarSyncManager,
                converter = converter,
                isDatabaseAvailable = (db != null)
            ) as T
        }
    }
}
