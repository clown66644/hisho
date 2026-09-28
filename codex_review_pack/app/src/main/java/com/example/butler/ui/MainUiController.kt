package com.example.butler.ui

import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.domain.logic.AiCommandConverter
import com.example.butler.domain.logic.ConfirmationRequiredException
import com.example.butler.domain.logic.ForbiddenOperationException
import com.example.butler.domain.logic.PriorityCalculator
import com.example.butler.domain.logic.UndoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainUiController internal constructor(
    private val todoDao: TodoDao,
    private val converter: AiCommandConverter,
    private val undoManager: UndoManager,
    private val priorityCalculator: PriorityCalculator = PriorityCalculator(),
) {
    constructor(database: AppDatabase) : this(
        todoDao = database.todoDao(),
        converter = AiCommandConverter(database),
        undoManager = UndoManager(database),
    )

    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    suspend fun initialize() = operationMutex.withLock {
        undoManager.restoreFromHistory(converter::restoreCommand)
        refresh("暗号化DBからデータを読み込みました。")
    }

    suspend fun submitAiAction(jsonString: String): Boolean = operationMutex.withLock {
        val command = try {
            converter.convertJsonToCommand(jsonString)
        } catch (_: ConfirmationRequiredException) {
            refresh("この操作には本人確認が必要なため実行しませんでした。")
            return@withLock false
        } catch (_: ForbiddenOperationException) {
            refresh("安全ポリシーにより禁止された操作です。")
            return@withLock false
        } catch (_: Exception) {
            refresh("操作候補の形式を確認できませんでした。")
            return@withLock false
        }

        val success = undoManager.executeCommand(command)
        refresh(if (success) "ToDoを保存しました。" else "ToDoを保存できませんでした。")
        success
    }

    suspend fun undo(): Boolean = operationMutex.withLock {
        val success = undoManager.undoLastCommand()
        refresh(if (success) "直前の操作を取り消しました。" else "取り消せる操作がありません。")
        success
    }

    suspend fun redo(): Boolean = operationMutex.withLock {
        val success = undoManager.redoNextCommand()
        refresh(if (success) "操作をやり直しました。" else "やり直せる操作がありません。")
        success
    }

    private suspend fun refresh(message: String) {
        val sorted = todoDao.getAllTodos()
            .sortedByDescending { priorityCalculator.calculatePriority(it.toDomainModel()).first }
            .map { it.toDomainModel() }
        _state.value = MainUiState(
            todos = sorted,
            canUndo = undoManager.canUndo(),
            canRedo = undoManager.canRedo(),
            statusMessage = message,
        )
    }
}
