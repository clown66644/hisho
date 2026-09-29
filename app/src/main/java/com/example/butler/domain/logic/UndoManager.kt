package com.example.butler.domain.logic

import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.OperationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface Command {
    val history: OperationHistory
    suspend fun execute(): Boolean
    suspend fun undo(): Boolean
    suspend fun redo(): Boolean = execute()
}

class UndoManager(
    private val historyDao: OperationHistoryDao? = null
) {
    private val commandHistory = mutableListOf<Command>()
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()
    private val executedOperationIds = mutableSetOf<String>()

    /**
     * 初期化およびDBからの既知操作IDロード
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (historyDao != null) {
            try {
                val saved = historyDao.getAllHistories()
                saved.forEach {
                    executedOperationIds.add(it.id)
                }
            } catch (e: Exception) { throw IllegalStateException("Failed to initialize History DB", e) }
        }
    }

    /**
     * 同一操作IDの二重実行をチェック
     */
    suspend fun isAlreadyExecuted(operationId: String): Boolean = withContext(Dispatchers.IO) {
        if (executedOperationIds.contains(operationId)) return@withContext true
        if (historyDao != null) {
            val exists = try {
                historyDao.getHistoryById(operationId) != null
            } catch (e: Exception) {
                throw IllegalStateException("Failed to read OperationHistory DB", e)
            }
            if (exists) {
                executedOperationIds.add(operationId)
                return@withContext true
            }
        }
        return@withContext false
    }

    /**
     * コマンドの実行 & トランザクション保存
     */
    suspend fun executeCommand(command: Command): Boolean = withContext(Dispatchers.IO) {
        if (historyDao != null) { try { historyDao.getHistoryById(command.history.id) } catch (e: Exception) { throw IllegalStateException("DB is unavailable", e) } }
        val opId = command.history.id

        // 二重登録・二重実行のブロック（冪等ガード）
        if (isAlreadyExecuted(opId)) {
            return@withContext false
        }

        // 1. コマンド実行
        val success = try {
            command.execute()
        } catch (e: Exception) {
            false
        }

        if (!success) {
            // Command失敗時は成功履歴を残さない
            return@withContext false
        }

        // 2. 履歴保存
        val updatedHistory = command.history.copy(status = OperationStatus.SUCCESS, isUndone = false)
        val dbSaved = if (historyDao != null) {
            try {
                val rowId = historyDao.insertHistory(OperationHistoryEntity.fromDomainModel(updatedHistory))
                rowId != -1L
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

        if (!dbSaved) {
            // 履歴保存失敗時に対象変更の補償処理（undo）を行い、成功状態にしない
            try {
                command.undo()
            } catch (e: Exception) {
                // 補償失敗時もメモリ整合性を維持
            }
            return@withContext false
        }

        // 3. メモリ状態の更新
        executedOperationIds.add(opId)
        commandHistory.add(command)
        undoStack.add(command)
        redoStack.clear()
        return@withContext true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun peekUndoCommand(): Command? = undoStack.lastOrNull()

    /**
     * Undo 実行
     */
    suspend fun undoLastCommand(): Boolean = withContext(Dispatchers.IO) {
        if (!canUndo()) return@withContext false

        // スタックから先走って削除せず参照
        val lastCommand = undoStack.last()

        if (historyDao != null) { try { historyDao.getHistoryById(lastCommand.history.id) } catch (e: Exception) { throw IllegalStateException("DB is unavailable", e) } }
        val undoSuccess = try {
            lastCommand.undo()
        } catch (e: Exception) {
            false
        }

        if (!undoSuccess) {
            // Undo 失敗時は元データと Undo スタック状態を保持
            return@withContext false
        }

        // 成功した場合のみスタックを移動し、DBを更新
        val updatedHistory = lastCommand.history.copy(isUndone = true)
        if (historyDao != null) {
            try {
                historyDao.updateHistory(OperationHistoryEntity.fromDomainModel(updatedHistory))
            } catch (e: Exception) {
                try {
                    lastCommand.redo()
                } catch (ce: Exception) {
                    throw IllegalStateException("Failed to update History DB and failed to compensate", ce)
                }
                throw IllegalStateException("Failed to update History DB, external action reverted", e)
            }
        }
        undoStack.removeAt(undoStack.size - 1)
        redoStack.add(lastCommand)

        

        return@withContext true
    }

    /**
     * Redo 実行
     */
    suspend fun redoNextCommand(): Boolean = withContext(Dispatchers.IO) {
        if (!canRedo()) return@withContext false

        val nextCommand = redoStack.last()

        if (historyDao != null) { try { historyDao.getHistoryById(nextCommand.history.id) } catch (e: Exception) { throw IllegalStateException("DB is unavailable", e) } }
        val redoSuccess = try {
            nextCommand.redo()
        } catch (e: Exception) {
            false
        }

        if (!redoSuccess) {
            // Redo 失敗時も状態保持
            return@withContext false
        }

        val updatedHistory = nextCommand.history.copy(isUndone = false)
        if (historyDao != null) {
            try {
                historyDao.updateHistory(OperationHistoryEntity.fromDomainModel(updatedHistory))
            } catch (e: Exception) {
                try {
                    nextCommand.undo()
                } catch (ce: Exception) {
                    throw IllegalStateException("Failed to update History DB and failed to compensate redo", ce)
                }
                throw IllegalStateException("Failed to update History DB, external redo reverted", e)
            }
        }
        redoStack.removeAt(redoStack.size - 1)
        undoStack.add(nextCommand)

        

        return@withContext true
    }

    /**
     * アプリ再起動相当時：DB上の履歴からUndoスタックを復元
     */
    suspend fun restoreFromHistory(commandResolver: (OperationHistory) -> Command?): Int = withContext(Dispatchers.IO) {
        if (historyDao == null) return@withContext 0

        val undoableList = try {
            historyDao.getUndoableHistories().map { it.toDomainModel() }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to restore history", e)
        }

        var restoredCount = 0
        undoStack.clear()
        redoStack.clear()

        for (history in undoableList) {
            try {
                val cmd = commandResolver(history)
                if (cmd != null) {
                    undoStack.add(cmd)
                    commandHistory.add(cmd)
                    executedOperationIds.add(history.id)
                    restoredCount++
                }
            } catch (e: Exception) {
                // 不明・破損した履歴JSONのコマンド生成失敗時はスキップし安全に継続
            }
        }
        return@withContext restoredCount
    }

    suspend fun getHistoryList(): List<OperationHistory> = withContext(Dispatchers.IO) {
        if (historyDao != null) {
            try {
                return@withContext historyDao.getAllHistories().map { it.toDomainModel() }
            } catch (e: Exception) {
                return@withContext commandHistory.map { it.history }
            }
        }
        return@withContext commandHistory.map { it.history }
    }
}
