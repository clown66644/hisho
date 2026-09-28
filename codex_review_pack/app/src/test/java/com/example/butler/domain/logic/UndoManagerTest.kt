package com.example.butler.domain.logic

import com.example.butler.data.local.OperationTransactionRunner
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.OperationPhase
import com.example.butler.domain.model.OperationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoManagerTest {
    private class FakeStore : OperationHistoryDao, OperationTransactionRunner {
        val histories = linkedMapOf<String, OperationHistoryEntity>()
        val targetValues = linkedMapOf<String, Int>()
        var failWrites = false
        var failNextWrites = 0
        private val transactionMutex = Mutex()

        override suspend fun <T> run(block: suspend () -> T): T =
            transactionMutex.withLock {
                val historySnapshot = LinkedHashMap(histories)
                val targetSnapshot = LinkedHashMap(targetValues)
                try {
                    block()
                } catch (error: Exception) {
                    histories.clear()
                    histories.putAll(historySnapshot)
                    targetValues.clear()
                    targetValues.putAll(targetSnapshot)
                    throw error
                }
            }

        override suspend fun getAllHistories(): List<OperationHistoryEntity> =
            histories.values.sortedBy { it.operationOrder }

        override suspend fun getHistoryById(id: String): OperationHistoryEntity? = histories[id]

        override suspend fun insertHistory(history: OperationHistoryEntity): Long {
            if (failWrites || failNextWrite() || histories.containsKey(history.id)) return -1
            histories[history.id] = history
            return histories.size.toLong()
        }

        override suspend fun updateHistory(history: OperationHistoryEntity): Int {
            if (failWrites || failNextWrite() || !histories.containsKey(history.id)) return 0
            histories[history.id] = history
            return 1
        }

        override suspend fun getNextOperationOrder(): Long =
            (histories.values.maxOfOrNull { it.operationOrder } ?: 0L) + 1L

        override suspend fun invalidateRedoHistories(updatedAt: Long): Int {
            if (failWrites || failNextWrite()) error("history write failed")
            var count = 0
            histories.replaceAll { _, history ->
                if (history.isUndone && history.isRedoable) {
                    count++
                    history.copy(isRedoable = false, updatedAt = updatedAt)
                } else {
                    history
                }
            }
            return count
        }

        private fun failNextWrite(): Boolean =
            if (failNextWrites > 0) {
                failNextWrites--
                true
            } else {
                false
            }
    }

    private class MapCommand(
        override val history: OperationHistory,
        private val store: FakeStore,
        private val executeResults: ArrayDeque<OperationStatus> =
            ArrayDeque(listOf(OperationStatus.SUCCESS)),
        private val undoResults: ArrayDeque<OperationStatus> =
            ArrayDeque(listOf(OperationStatus.SUCCESS)),
        private val redoResults: ArrayDeque<OperationStatus> =
            ArrayDeque(listOf(OperationStatus.SUCCESS)),
        private val compensationResult: OperationStatus = OperationStatus.SUCCESS,
        private val executeDelayMillis: Long = 0,
        private val undoDelayMillis: Long = 0,
        private val redoDelayMillis: Long = 0,
        private val compensationDelayMillis: Long = 0,
    ) : Command {
        var executeCount = 0
        var undoCount = 0
        var redoCount = 0

        override suspend fun execute(): OperationStatus {
            executeCount++
            if (executeDelayMillis > 0) delay(executeDelayMillis)
            store.targetValues[history.targetId] = 1
            return executeResults.removeFirstOrNull() ?: OperationStatus.SUCCESS
        }

        override suspend fun undo(): OperationStatus {
            undoCount++
            if (undoDelayMillis > 0) delay(undoDelayMillis)
            store.targetValues.remove(history.targetId)
            return undoResults.removeFirstOrNull() ?: OperationStatus.SUCCESS
        }

        override suspend fun redo(): OperationStatus {
            redoCount++
            if (redoDelayMillis > 0) delay(redoDelayMillis)
            store.targetValues[history.targetId] = 1
            return redoResults.removeFirstOrNull() ?: OperationStatus.SUCCESS
        }

        override suspend fun compensate(phase: OperationPhase): OperationStatus {
            if (compensationDelayMillis > 0) delay(compensationDelayMillis)
            return compensationResult
        }
    }

    private lateinit var store: FakeStore
    private lateinit var manager: UndoManager
    private var now = 1_000L

    @Before
    fun setUp() {
        store = FakeStore()
        manager = UndoManager(store, store) { ++now }
    }

    private fun history(id: String, targetId: String = id) = OperationHistory(
        id = id,
        timestamp = now,
        actor = Actor.USER,
        actionType = "CREATE_TODO",
        targetId = targetId,
    )

    @Test
    fun commandSuccessAndHistoryCommitTogether() = runBlocking {
        val command = MapCommand(history("success"), store)

        assertTrue(manager.executeCommand(command))

        assertEquals(1, store.targetValues["success"])
        assertEquals(OperationStatus.SUCCESS, store.histories["success"]?.toDomainModel()?.status)
        assertTrue(manager.canUndo())
    }

    @Test
    fun commandFailureRollsBackTargetAndLeavesNoSuccessHistory() = runBlocking {
        val command = MapCommand(
            history("command-failure"),
            store,
            executeResults = ArrayDeque(listOf(OperationStatus.FAILED)),
        )

        assertFalse(manager.executeCommand(command))

        assertFalse(store.targetValues.containsKey("command-failure"))
        assertEquals(OperationStatus.FAILED, store.histories["command-failure"]?.toDomainModel()?.status)
        assertFalse(manager.canUndo())
    }

    @Test
    fun historyWriteFailureRollsBackOnlyTargetMutation() = runBlocking {
        store.targetValues["unrelated"] = 99
        store.failWrites = true

        assertFalse(manager.executeCommand(MapCommand(history("write-failure"), store)))

        assertFalse(store.targetValues.containsKey("write-failure"))
        assertEquals(99, store.targetValues["unrelated"])
        assertFalse(manager.canUndo())
    }

    @Test
    fun dbOnlyRollbackIsRecordedAsFailedRatherThanPartialSuccess() = runBlocking {
        val operation = history("db-only-rollback")
        val command = object : Command {
            override val history = operation

            override suspend fun execute(): OperationStatus {
                store.targetValues[history.targetId] = 1
                return OperationStatus.SUCCESS
            }

            override suspend fun undo(): OperationStatus {
                store.targetValues.remove(history.targetId)
                return OperationStatus.SUCCESS
            }
        }
        store.failNextWrites = 1

        assertFalse(manager.executeCommand(command))

        assertFalse(store.targetValues.containsKey(operation.targetId))
        assertEquals(
            OperationStatus.FAILED,
            store.histories[operation.id]?.toDomainModel()?.status,
        )
        assertEquals(
            OperationStatus.SUCCESS,
            store.histories[operation.id]?.toDomainModel()?.compensationResult,
        )
    }

    @Test
    fun duplicateSuccessfulOperationIdExecutesOnce() = runBlocking {
        val first = MapCommand(history("duplicate"), store)
        val duplicate = MapCommand(history("duplicate"), store)

        assertTrue(manager.executeCommand(first))
        assertTrue(manager.executeCommand(duplicate))

        assertEquals(1, first.executeCount)
        assertEquals(0, duplicate.executeCount)
        assertEquals(1, store.histories.size)
    }

    @Test
    fun reusedOperationIdWithDifferentTargetIsRejected() = runBlocking {
        val first = MapCommand(history("reused-id", targetId = "first-target"), store)
        val conflicting = MapCommand(history("reused-id", targetId = "second-target"), store)

        assertTrue(manager.executeCommand(first))
        assertFalse(manager.executeCommand(conflicting))

        assertEquals(1, first.executeCount)
        assertEquals(0, conflicting.executeCount)
        assertEquals(1, store.targetValues["first-target"])
        assertFalse(store.targetValues.containsKey("second-target"))
    }

    @Test
    fun undoneOperationIdIsNotReportedAsApplied() = runBlocking {
        val original = MapCommand(history("undone-duplicate"), store)
        assertTrue(manager.executeCommand(original))
        assertTrue(manager.undoLastCommand())

        val duplicate = MapCommand(history("undone-duplicate"), store)

        assertFalse(manager.executeCommand(duplicate))
        assertEquals(0, duplicate.executeCount)
        assertFalse(store.targetValues.containsKey("undone-duplicate"))
    }

    @Test
    fun undoSuccessKeepsDataHistoryAndStacksConsistent() = runBlocking {
        val command = MapCommand(history("undo-success"), store)
        assertTrue(manager.executeCommand(command))

        assertTrue(manager.undoLastCommand())

        val saved = store.histories["undo-success"]!!.toDomainModel()
        assertFalse(store.targetValues.containsKey("undo-success"))
        assertTrue(saved.isUndone)
        assertEquals(OperationPhase.UNDO, saved.lastPhase)
        assertEquals(OperationStatus.SUCCESS, saved.lastResult)
        assertFalse(manager.canUndo())
        assertTrue(manager.canRedo())
    }

    @Test
    fun undoFailureRollsBackDataAndRemainsRetryable() = runBlocking {
        val command = MapCommand(
            history("undo-failure"),
            store,
            undoResults = ArrayDeque(listOf(OperationStatus.FAILED, OperationStatus.SUCCESS)),
        )
        assertTrue(manager.executeCommand(command))

        assertFalse(manager.undoLastCommand())
        assertEquals(1, store.targetValues["undo-failure"])
        assertTrue(manager.canUndo())
        assertFalse(manager.canRedo())
        assertEquals(
            OperationStatus.FAILED,
            store.histories["undo-failure"]!!.toDomainModel().lastResult,
        )

        assertTrue(manager.undoLastCommand())
        assertFalse(store.targetValues.containsKey("undo-failure"))
    }

    @Test
    fun undoHistoryWriteFailureRollsBackAndPreservesUndoStack() = runBlocking {
        val command = MapCommand(history("undo-write-failure"), store)
        assertTrue(manager.executeCommand(command))
        store.failWrites = true

        assertFalse(manager.undoLastCommand())

        assertEquals(1, store.targetValues["undo-write-failure"])
        assertTrue(manager.canUndo())
        assertFalse(manager.canRedo())
    }

    @Test
    fun redoSuccessAndFailurePreserveConsistency() = runBlocking {
        val command = MapCommand(
            history("redo"),
            store,
            redoResults = ArrayDeque(listOf(OperationStatus.FAILED, OperationStatus.SUCCESS)),
        )
        assertTrue(manager.executeCommand(command))
        assertTrue(manager.undoLastCommand())

        assertFalse(manager.redoNextCommand())
        assertFalse(store.targetValues.containsKey("redo"))
        assertTrue(manager.canRedo())
        assertEquals(OperationStatus.FAILED, store.histories["redo"]!!.toDomainModel().lastResult)

        assertTrue(manager.redoNextCommand())
        assertEquals(1, store.targetValues["redo"])
        assertFalse(manager.canRedo())
        assertTrue(manager.canUndo())
    }

    @Test
    fun restoredManagerCanUndoAfterRestart() = runBlocking {
        assertTrue(manager.executeCommand(MapCommand(history("restore"), store)))
        val restored = UndoManager(store, store) { ++now }

        assertEquals(1, restored.restoreFromHistory { MapCommand(it, store) })
        assertTrue(restored.canUndo())
        assertTrue(restored.undoLastCommand())
        assertFalse(store.targetValues.containsKey("restore"))
    }

    @Test
    fun multipleCommandsUndoInReverseOrder() = runBlocking {
        val first = MapCommand(history("first"), store)
        val second = MapCommand(history("second"), store)
        assertTrue(manager.executeCommand(first))
        assertTrue(manager.executeCommand(second))

        assertTrue(manager.undoLastCommand())
        assertEquals(1, store.targetValues["first"])
        assertFalse(store.targetValues.containsKey("second"))

        assertTrue(manager.undoLastCommand())
        assertFalse(store.targetValues.containsKey("first"))
    }

    @Test
    fun corruptedOrUnknownHistoryIsNeverExecutedDuringRestore() = runBlocking {
        val valid = MapCommand(history("valid"), store)
        val corrupt = MapCommand(history("corrupt"), store)
        assertTrue(manager.executeCommand(valid))
        assertTrue(manager.executeCommand(corrupt))
        val restored = UndoManager(store, store) { ++now }
        var resolverExecutions = 0

        val count = restored.restoreFromHistory {
            if (it.id == "corrupt") error("invalid history JSON")
            resolverExecutions++
            MapCommand(it, store)
        }

        assertEquals(1, count)
        assertEquals(1, resolverExecutions)
        assertEquals(0, valid.undoCount)
        assertEquals(0, corrupt.undoCount)
    }

    @Test
    fun partialResultAndCompensationAreRecorded() = runBlocking {
        val command = MapCommand(
            history("partial"),
            store,
            executeResults = ArrayDeque(listOf(OperationStatus.PARTIAL_SUCCESS)),
            compensationResult = OperationStatus.SUCCESS,
        )

        assertFalse(manager.executeCommand(command))

        val saved = store.histories["partial"]!!.toDomainModel()
        assertEquals(OperationStatus.PARTIAL_SUCCESS, saved.lastResult)
        assertEquals(OperationStatus.SUCCESS, saved.compensationResult)
        assertFalse(store.targetValues.containsKey("partial"))
    }

    @Test
    fun newCommandInvalidatesPersistedRedoBranch() = runBlocking {
        assertTrue(manager.executeCommand(MapCommand(history("old-branch"), store)))
        assertTrue(manager.undoLastCommand())
        assertTrue(manager.canRedo())

        assertTrue(manager.executeCommand(MapCommand(history("new-branch"), store)))
        assertFalse(manager.canRedo())
        assertFalse(store.histories["old-branch"]!!.isRedoable)

        val restored = UndoManager(store, store) { ++now }
        assertEquals(1, restored.restoreFromHistory { MapCommand(it, store) })
        assertFalse(restored.canRedo())
        assertTrue(restored.canUndo())
    }

    @Test
    fun concurrentManagersExecuteSameOperationIdOnlyOnce() = runBlocking {
        val firstManager = UndoManager(store, store) { ++now }
        val secondManager = UndoManager(store, store) { ++now }
        val first = MapCommand(
            history("concurrent-operation"),
            store,
            executeDelayMillis = 50,
        )
        val second = MapCommand(history("concurrent-operation"), store)

        val firstResult = async(Dispatchers.Default) { firstManager.executeCommand(first) }
        delay(10)
        val secondResult = async(Dispatchers.Default) { secondManager.executeCommand(second) }

        assertTrue(firstResult.await())
        assertTrue(secondResult.await())
        assertEquals(1, first.executeCount + second.executeCount)
        assertEquals(1, store.histories.size)
        assertEquals(1, store.targetValues["concurrent-operation"])
    }

    @Test
    fun concurrentManagersUndoSameOperationOnlyOnce() = runBlocking {
        assertTrue(manager.executeCommand(MapCommand(history("concurrent-undo"), store)))
        val firstManager = UndoManager(store, store) { ++now }
        val secondManager = UndoManager(store, store) { ++now }
        lateinit var firstCommand: MapCommand
        lateinit var secondCommand: MapCommand
        firstManager.restoreFromHistory {
            MapCommand(it, store, undoDelayMillis = 50).also { command ->
                firstCommand = command
            }
        }
        secondManager.restoreFromHistory {
            MapCommand(it, store).also { command -> secondCommand = command }
        }

        val firstResult = async(Dispatchers.Default) { firstManager.undoLastCommand() }
        delay(10)
        val secondResult = async(Dispatchers.Default) { secondManager.undoLastCommand() }

        assertTrue(firstResult.await())
        assertTrue(secondResult.await())
        assertEquals(1, firstCommand.undoCount + secondCommand.undoCount)
        assertFalse(store.targetValues.containsKey("concurrent-undo"))
        assertTrue(store.histories["concurrent-undo"]!!.isUndone)
        assertTrue(firstManager.canRedo())
        assertTrue(secondManager.canRedo())
    }

    @Test
    fun staleManagerCannotReviveInvalidatedRedoBranch() = runBlocking {
        assertTrue(manager.executeCommand(MapCommand(history("stale-redo"), store)))
        assertTrue(manager.undoLastCommand())
        val branchOwner = UndoManager(store, store) { ++now }
        val staleManager = UndoManager(store, store) { ++now }
        branchOwner.restoreFromHistory { MapCommand(it, store) }
        lateinit var staleCommand: MapCommand
        staleManager.restoreFromHistory {
            MapCommand(it, store, redoDelayMillis = 50).also { command ->
                staleCommand = command
            }
        }
        assertTrue(staleManager.canRedo())

        assertTrue(branchOwner.executeCommand(MapCommand(history("replacement"), store)))
        assertFalse(staleManager.redoNextCommand())

        assertEquals(0, staleCommand.redoCount)
        assertFalse(staleManager.canRedo())
        assertFalse(store.histories["stale-redo"]!!.isRedoable)
        assertFalse(store.targetValues.containsKey("stale-redo"))
    }

    @Test
    fun delayedFailureRecordCannotOverwriteNewerSuccess() = runBlocking {
        val failingManager = UndoManager(store, store) { ++now }
        val succeedingManager = UndoManager(store, store) { ++now }
        val failing = MapCommand(
            history("failure-race"),
            store,
            executeResults = ArrayDeque(listOf(OperationStatus.FAILED)),
            compensationDelayMillis = 50,
        )
        val succeeding = MapCommand(history("failure-race"), store)

        val failedResult = async(Dispatchers.Default) {
            failingManager.executeCommand(failing)
        }
        delay(10)
        val successResult = async(Dispatchers.Default) {
            succeedingManager.executeCommand(succeeding)
        }

        assertFalse(failedResult.await())
        assertTrue(successResult.await())
        val persisted = store.histories["failure-race"]!!.toDomainModel()
        assertEquals(OperationStatus.SUCCESS, persisted.status)
        assertEquals(OperationStatus.SUCCESS, persisted.lastResult)
        assertEquals(1, store.targetValues["failure-race"])
    }

    @Test
    fun restartRestoresSameTimestampCommandsInExecutionOrder() = runBlocking {
        val sharedTimestamp = 5_000L
        val first = MapCommand(
            OperationHistory(
                id = "z-first",
                timestamp = sharedTimestamp,
                actor = Actor.USER,
                actionType = "CREATE_TODO",
                targetId = "first-target",
            ),
            store,
        )
        val second = MapCommand(
            OperationHistory(
                id = "a-second",
                timestamp = sharedTimestamp,
                actor = Actor.USER,
                actionType = "CREATE_TODO",
                targetId = "second-target",
            ),
            store,
        )
        assertTrue(manager.executeCommand(first))
        assertTrue(manager.executeCommand(second))
        val restored = UndoManager(store, store) { ++now }
        val restoredCommands = mutableMapOf<String, MapCommand>()
        assertEquals(
            2,
            restored.restoreFromHistory {
                MapCommand(it, store).also { command ->
                    restoredCommands[it.id] = command
                }
            },
        )

        assertTrue(restored.undoLastCommand())

        assertFalse(store.targetValues.containsKey("second-target"))
        assertEquals(1, store.targetValues["first-target"])
        assertEquals(1, restoredCommands["a-second"]?.undoCount)
        assertEquals(0, restoredCommands["z-first"]?.undoCount)
    }

    @Test
    fun successfulRetryGetsOrderOfSuccessfulExecution() = runBlocking {
        val retrying = MapCommand(
            history("retry-after-failure", "retry-target"),
            store,
            executeResults = ArrayDeque(
                listOf(OperationStatus.FAILED, OperationStatus.SUCCESS)
            ),
        )
        assertFalse(manager.executeCommand(retrying))
        assertTrue(
            manager.executeCommand(
                MapCommand(history("middle-success", "middle-target"), store)
            )
        )
        assertTrue(manager.executeCommand(retrying))
        val restored = UndoManager(store, store) { ++now }
        val restoredCommands = mutableMapOf<String, MapCommand>()
        assertEquals(
            2,
            restored.restoreFromHistory {
                MapCommand(it, store).also { command ->
                    restoredCommands[it.id] = command
                }
            },
        )

        assertTrue(restored.undoLastCommand())

        assertFalse(store.targetValues.containsKey("retry-target"))
        assertEquals(1, store.targetValues["middle-target"])
        assertEquals(1, restoredCommands["retry-after-failure"]?.undoCount)
        assertEquals(0, restoredCommands["middle-success"]?.undoCount)
    }
}
