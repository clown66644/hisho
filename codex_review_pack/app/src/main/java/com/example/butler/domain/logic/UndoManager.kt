package com.example.butler.domain.logic

import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.OperationTransactionRunner
import com.example.butler.data.local.RoomOperationTransactionRunner
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.OperationPhase
import com.example.butler.domain.model.OperationStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * CommandのローカルDB更新は、必ずUndoManagerへ渡したAppDatabaseのDAOで行うこと。
 * これにより対象データと履歴が同じRoomトランザクションに参加する。
 *
 * OS予約などDB外の副作用を持つCommandは、トランザクション失敗時に
 * [compensate]で副作用を取り消す。補償結果も履歴へ保存する。
 */
interface Command {
    val history: OperationHistory
    suspend fun execute(): OperationStatus
    suspend fun undo(): OperationStatus
    suspend fun redo(): OperationStatus = execute()
    /**
     * DB外の副作用がないCommandはRoomのロールバックだけで復旧済みとなる。
     * OS予約など外部副作用を持つCommandだけがこのメソッドをoverrideする。
     */
    suspend fun compensate(phase: OperationPhase): OperationStatus = OperationStatus.SUCCESS
}

class UndoManager(
    private val historyDao: OperationHistoryDao,
    private val transactionRunner: OperationTransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(database: AppDatabase) : this(
        historyDao = database.operationHistoryDao(),
        transactionRunner = RoomOperationTransactionRunner(database),
    )

    private val stateMutex = Mutex()
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()

    suspend fun executeCommand(command: Command): Boolean = stateMutex.withLock {
        val existing = safelyRead(command.history.id)
        if (existing?.status == OperationStatus.SUCCESS) {
            // 同一IDでも内容が異なる要求や、利用者がUndo済みの操作は成功扱いしない。
            return@withLock !existing.isUndone && isSameOperation(existing, command.history)
        }

        var baseHistory = existing ?: command.history
        var attempt = (existing?.attemptCount ?: 0) + 1
        var commandResult = OperationStatus.FAILED
        var commandStarted = false
        var alreadyCompletedInTransaction = false
        try {
            transactionRunner.run {
                // Managerが複数存在しても、同じDBトランザクション内で再確認して
                // 成功済み操作の外部副作用を二重実行しない。
                val current = historyDao.getHistoryById(command.history.id)?.toDomainModel()
                if (current?.status == OperationStatus.SUCCESS) {
                    alreadyCompletedInTransaction =
                        !current.isUndone && isSameOperation(current, command.history)
                    if (!alreadyCompletedInTransaction) {
                        throw OperationIdConflict()
                    }
                    return@run
                }
                baseHistory = current ?: command.history
                attempt = (current?.attemptCount ?: 0) + 1
                // 失敗履歴の再試行も「成功した時点」の順序でUndo対象に並べる。
                val operationOrder = historyDao.getNextOperationOrder()
                commandStarted = true
                commandResult = command.execute()
                requireSuccess(commandResult)
                persist(
                    command.history.copy(
                        status = OperationStatus.SUCCESS,
                        isUndone = false,
                        isRedoable = false,
                        lastPhase = OperationPhase.EXECUTE,
                        lastResult = OperationStatus.SUCCESS,
                        attemptCount = attempt,
                        updatedAt = clock(),
                        compensationResult = null,
                        operationOrder = operationOrder,
                    ),
                    exists = current != null,
                )
                // 新規操作が成功した時点で、以前のRedo分岐は永続的にも無効化する。
                historyDao.invalidateRedoHistories(clock())
            }
        } catch (_: Exception) {
            val compensation = if (commandStarted) {
                safelyCompensate(command, OperationPhase.EXECUTE)
            } else {
                null
            }
            recordFailedTransition(
                base = baseHistory,
                phase = OperationPhase.EXECUTE,
                result = commandResult,
                attempt = attempt,
                compensation = compensation,
            )
            return@withLock false
        }

        if (alreadyCompletedInTransaction) return@withLock true
        undoStack += command
        redoStack.clear()
        true
    }

    suspend fun undoLastCommand(): Boolean = stateMutex.withLock {
        val command = undoStack.lastOrNull() ?: return@withLock false
        val existing = safelyRead(command.history.id) ?: return@withLock false
        var baseHistory = existing
        var result = OperationStatus.FAILED
        var commandStarted = false
        var alreadyUndone = false

        try {
            transactionRunner.run {
                val current = historyDao.getHistoryById(command.history.id)?.toDomainModel()
                    ?: error("History not found")
                baseHistory = current
                if (current.isUndone) {
                    alreadyUndone = true
                    return@run
                }
                commandStarted = true
                result = command.undo()
                requireSuccess(result)
                persist(
                    current.copy(
                        isUndone = true,
                        isRedoable = true,
                        lastPhase = OperationPhase.UNDO,
                        lastResult = OperationStatus.SUCCESS,
                        attemptCount = current.attemptCount + 1,
                        updatedAt = clock(),
                        compensationResult = null,
                    ),
                    exists = true,
                )
            }
        } catch (_: Exception) {
            val compensation = if (commandStarted) {
                safelyCompensate(command, OperationPhase.UNDO)
            } else {
                null
            }
            recordFailedTransition(
                base = baseHistory,
                phase = OperationPhase.UNDO,
                result = result,
                attempt = baseHistory.attemptCount + 1,
                compensation = compensation,
            )
            return@withLock false
        }

        undoStack.removeAt(undoStack.lastIndex)
        // 他Managerが既にUndoし、その後Redo分岐を無効化していた場合は復活させない。
        if (!alreadyUndone || baseHistory.isRedoable) redoStack += command
        true
    }

    suspend fun redoNextCommand(): Boolean = stateMutex.withLock {
        val command = redoStack.lastOrNull() ?: return@withLock false
        val existing = safelyRead(command.history.id) ?: return@withLock false
        var baseHistory = existing
        var result = OperationStatus.FAILED
        var commandStarted = false
        var redoInvalidated = false

        try {
            transactionRunner.run {
                val current = historyDao.getHistoryById(command.history.id)?.toDomainModel()
                    ?: error("History not found")
                baseHistory = current
                if (!current.isUndone) {
                    return@run
                }
                if (!current.isRedoable) {
                    redoInvalidated = true
                    return@run
                }
                commandStarted = true
                result = command.redo()
                requireSuccess(result)
                persist(
                    current.copy(
                        isUndone = false,
                        isRedoable = false,
                        lastPhase = OperationPhase.REDO,
                        lastResult = OperationStatus.SUCCESS,
                        attemptCount = current.attemptCount + 1,
                        updatedAt = clock(),
                        compensationResult = null,
                    ),
                    exists = true,
                )
            }
        } catch (_: Exception) {
            val compensation = if (commandStarted) {
                safelyCompensate(command, OperationPhase.REDO)
            } else {
                null
            }
            recordFailedTransition(
                base = baseHistory,
                phase = OperationPhase.REDO,
                result = result,
                attempt = baseHistory.attemptCount + 1,
                compensation = compensation,
            )
            return@withLock false
        }

        if (redoInvalidated) {
            redoStack.removeAt(redoStack.lastIndex)
            return@withLock false
        }
        redoStack.removeAt(redoStack.lastIndex)
        undoStack += command
        true
    }

    suspend fun restoreFromHistory(
        commandResolver: (OperationHistory) -> Command?,
    ): Int = stateMutex.withLock {
        val histories = try {
            historyDao.getAllHistories()
                .map { it.toDomainModel() }
                .filter { it.status == OperationStatus.SUCCESS }
        } catch (_: Exception) {
            return@withLock 0
        }

        val restoredUndo = mutableListOf<Command>()
        val restoredRedo = mutableListOf<Command>()
        for (history in histories) {
            val command = try {
                commandResolver(history)
            } catch (_: Exception) {
                null
            } ?: continue

            if (history.isUndone && history.isRedoable) {
                restoredRedo.add(0, command)
            } else if (!history.isUndone) {
                restoredUndo += command
            }
        }

        undoStack.clear()
        undoStack += restoredUndo
        redoStack.clear()
        redoStack += restoredRedo
        restoredUndo.size + restoredRedo.size
    }

    suspend fun getHistoryList(): List<OperationHistory> = stateMutex.withLock {
        try {
            historyDao.getAllHistories().map { it.toDomainModel() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun canUndo(): Boolean = stateMutex.withLock { undoStack.isNotEmpty() }
    suspend fun canRedo(): Boolean = stateMutex.withLock { redoStack.isNotEmpty() }

    private suspend fun safelyRead(id: String): OperationHistory? = try {
        historyDao.getHistoryById(id)?.toDomainModel()
    } catch (_: Exception) {
        null
    }

    private suspend fun persist(history: OperationHistory, exists: Boolean) {
        val entity = OperationHistoryEntity.fromDomainModel(history)
        if (exists) {
            check(historyDao.updateHistory(entity) == 1) { "History update failed" }
        } else {
            check(historyDao.insertHistory(entity) != -1L) { "History insert failed" }
        }
    }

    private suspend fun recordFailedTransition(
        base: OperationHistory,
        phase: OperationPhase,
        result: OperationStatus,
        attempt: Int,
        compensation: OperationStatus?,
    ) {
        val safeResult = if (result == OperationStatus.SUCCESS) {
            if (compensation == OperationStatus.SUCCESS) OperationStatus.FAILED
            else OperationStatus.PARTIAL_SUCCESS
        } else {
            result
        }
        val failed = base.copy(
            status = if (phase == OperationPhase.EXECUTE) safeResult else base.status,
            lastPhase = phase,
            lastResult = safeResult,
            attemptCount = attempt,
            updatedAt = clock(),
            compensationResult = compensation,
        )
        try {
            transactionRunner.run {
                val current = historyDao.getHistoryById(base.id)?.toDomainModel()
                // 補償処理中に別Managerが成功・Undo・Redoを確定した場合、
                // 遅れて到着した失敗記録で新しい状態を巻き戻さない。
                if (current != null && hasStateChangedSince(base, current)) return@run
                if (current == null && phase != OperationPhase.EXECUTE) return@run
                val orderedFailure = if (current == null) {
                    failed.copy(operationOrder = historyDao.getNextOperationOrder())
                } else {
                    failed
                }
                persist(orderedFailure, exists = current != null)
            }
        } catch (_: Exception) {
            // 履歴ストレージ自体が失敗している場合は、機密情報を露出せず失敗を返す。
        }
    }

    private fun hasStateChangedSince(
        base: OperationHistory,
        current: OperationHistory,
    ): Boolean =
        base.status != current.status ||
            base.isUndone != current.isUndone ||
            base.isRedoable != current.isRedoable ||
            base.attemptCount != current.attemptCount ||
            base.lastPhase != current.lastPhase ||
            base.lastResult != current.lastResult

    private suspend fun safelyCompensate(
        command: Command,
        phase: OperationPhase,
    ): OperationStatus? = try {
        command.compensate(phase)
    } catch (_: Exception) {
        OperationStatus.FAILED
    }

    private fun requireSuccess(result: OperationStatus) {
        if (result != OperationStatus.SUCCESS) throw CommandNotCompleted()
    }

    private fun isSameOperation(
        existing: OperationHistory,
        incoming: OperationHistory,
    ): Boolean =
        existing.actionType == incoming.actionType &&
            existing.targetId == incoming.targetId &&
            existing.previousStateJson == incoming.previousStateJson &&
            existing.newStateJson == incoming.newStateJson

    private class CommandNotCompleted : RuntimeException()
    private class OperationIdConflict : RuntimeException()
}
