package com.example.butler.domain.logic

import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoManagerTest {

    private class FakeOperationHistoryDao : OperationHistoryDao {
        val db = mutableMapOf<String, OperationHistoryEntity>()
        var shouldFailInsert = false

        override suspend fun getAllHistories(): List<OperationHistoryEntity> {
            return db.values.sortedBy { it.timestamp }
        }

        override suspend fun getHistoryById(id: String): OperationHistoryEntity? {
            return db[id]
        }

        override suspend fun getUndoableHistories(): List<OperationHistoryEntity> {
            return db.values.filter { !it.isUndone && it.status == "SUCCESS" }.sortedBy { it.timestamp }
        }

        override suspend fun insertHistory(history: OperationHistoryEntity): Long {
            if (shouldFailInsert) return -1L
            db[history.id] = history
            return 1L
        }

        override suspend fun updateHistory(history: OperationHistoryEntity) {
            db[history.id] = history
        }

        override suspend fun deleteHistoryById(id: String) {
            db.remove(id)
        }
    }

    private class MockTestCommand(
        override val history: OperationHistory,
        private val shouldExecuteSucceed: Boolean = true,
        private val shouldUndoSucceed: Boolean = true,
        private val shouldRedoSucceed: Boolean = true
    ) : Command {
        var isExecuted = false
        var isUndone = false
        var isRedone = false

        override suspend fun execute(): Boolean {
            if (shouldExecuteSucceed) {
                isExecuted = true
                isUndone = false
                return true
            }
            return false
        }

        override suspend fun undo(): Boolean {
            if (shouldUndoSucceed) {
                isUndone = true
                isExecuted = false
                return true
            }
            return false
        }

        override suspend fun redo(): Boolean {
            if (shouldRedoSucceed) {
                isRedone = true
                isExecuted = true
                isUndone = false
                return true
            }
            return false
        }
    }

    private lateinit var fakeDao: FakeOperationHistoryDao
    private lateinit var undoManager: UndoManager

    @Before
    fun setUp() = runBlocking {
        fakeDao = FakeOperationHistoryDao()
        undoManager = UndoManager(fakeDao)
        undoManager.initialize()
    }

    @Test
    fun testExecuteAndUndoCommandBasic() = runBlocking {
        val history = OperationHistory(
            id = "op-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-123"
        )
        val command = MockTestCommand(history)

        assertTrue(undoManager.executeCommand(command))
        assertTrue(command.isExecuted)
        assertTrue(undoManager.canUndo())

        assertTrue(undoManager.undoLastCommand())
        assertTrue(command.isUndone)
        assertFalse(undoManager.canUndo())
    }

    @Test
    fun testCommandSuccessAndHistorySaved() = runBlocking {
        val history = OperationHistory(
            id = "op-success-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-1"
        )
        val command = MockTestCommand(history, shouldExecuteSucceed = true)

        val result = undoManager.executeCommand(command)
        assertTrue(result)
        assertTrue(command.isExecuted)

        val savedInDb = fakeDao.db["op-success-1"]
        assertTrue(savedInDb != null)
        assertEquals("SUCCESS", savedInDb?.status)
        assertEquals(false, savedInDb?.isUndone)
    }

    @Test
    fun testCommandFailureNoSuccessHistory() = runBlocking {
        val history = OperationHistory(
            id = "op-fail-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-2"
        )
        val command = MockTestCommand(history, shouldExecuteSucceed = false)

        val result = undoManager.executeCommand(command)
        assertFalse(result)
        assertFalse(command.isExecuted)

        val savedInDb = fakeDao.db["op-fail-1"]
        assertEquals(null, savedInDb)
    }

    @Test
    fun testHistorySaveFailureRevertsCommand() = runBlocking {
        fakeDao.shouldFailInsert = true

        val history = OperationHistory(
            id = "op-db-fail-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-3"
        )
        val command = MockTestCommand(history, shouldExecuteSucceed = true, shouldUndoSucceed = true)

        val result = undoManager.executeCommand(command)
        assertFalse(result)
        assertTrue(command.isUndone)
        assertFalse(undoManager.canUndo())
    }

    @Test
    fun testDuplicateOperationIdPrevented() = runBlocking {
        val history = OperationHistory(
            id = "op-dup-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-4"
        )
        val command1 = MockTestCommand(history)
        val command2 = MockTestCommand(history)

        assertTrue(undoManager.executeCommand(command1))
        assertFalse(undoManager.executeCommand(command2))
        assertEquals(1, fakeDao.db.size)
    }

    @Test
    fun testUndoSuccessHistoryMatches() = runBlocking {
        val history = OperationHistory(
            id = "op-undo-match-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-5"
        )
        val command = MockTestCommand(history)

        undoManager.executeCommand(command)
        assertTrue(undoManager.undoLastCommand())

        val savedInDb = fakeDao.db["op-undo-match-1"]
        assertTrue(savedInDb != null)
        assertTrue(savedInDb!!.isUndone)
        assertTrue(command.isUndone)
    }

    @Test
    fun testUndoFailurePreservesState() = runBlocking {
        val history = OperationHistory(
            id = "op-undo-fail-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-6"
        )
        val command = MockTestCommand(history, shouldExecuteSucceed = true, shouldUndoSucceed = false)

        undoManager.executeCommand(command)
        val undoResult = undoManager.undoLastCommand()

        assertFalse(undoResult)
        assertTrue(undoManager.canUndo())
        assertFalse(command.isUndone)
    }

    @Test
    fun testRedoSuccessAndFailure() = runBlocking {
        val history = OperationHistory(
            id = "op-redo-1",
            actor = Actor.USER,
            actionType = "CREATE_TODO",
            targetId = "todo-7"
        )
        val command = MockTestCommand(history, shouldExecuteSucceed = true, shouldUndoSucceed = true, shouldRedoSucceed = true)

        undoManager.executeCommand(command)
        undoManager.undoLastCommand()
        assertTrue(undoManager.canRedo())

        assertTrue(undoManager.redoNextCommand())
        assertTrue(command.isRedone)
        assertFalse(undoManager.canRedo())
        assertTrue(undoManager.canUndo())

        val savedInDb = fakeDao.db["op-redo-1"]
        assertFalse(savedInDb!!.isUndone)
    }

    @Test
    fun testRestoreFromHistoryAfterAppRestart() = runBlocking {
        val history1 = OperationHistory(id = "op-rest-1", actor = Actor.USER, actionType = "CREATE_TODO", targetId = "t1")
        val history2 = OperationHistory(id = "op-rest-2", actor = Actor.USER, actionType = "CREATE_TODO", targetId = "t2")

        val cmd1 = MockTestCommand(history1)
        val cmd2 = MockTestCommand(history2)

        undoManager.executeCommand(cmd1)
        undoManager.executeCommand(cmd2)

        val newUndoManager = UndoManager(fakeDao)
        newUndoManager.initialize()
        val restoredCount = newUndoManager.restoreFromHistory { h ->
            when (h.id) {
                "op-rest-1" -> MockTestCommand(h)
                "op-rest-2" -> MockTestCommand(h)
                else -> null
            }
        }

        assertEquals(2, restoredCount)
        assertTrue(newUndoManager.canUndo())
    }

    @Test
    fun testMultipleCommandsReverseUndo() = runBlocking {
        val cmd1 = MockTestCommand(OperationHistory(id = "op-multi-1", actor = Actor.USER, actionType = "CREATE_TODO", targetId = "t1"))
        val cmd2 = MockTestCommand(OperationHistory(id = "op-multi-2", actor = Actor.USER, actionType = "CREATE_TODO", targetId = "t2"))

        undoManager.executeCommand(cmd1)
        undoManager.executeCommand(cmd2)

        assertTrue(undoManager.undoLastCommand())
        assertTrue(cmd2.isUndone)
        assertFalse(cmd1.isUndone)

        assertTrue(undoManager.undoLastCommand())
        assertTrue(cmd1.isUndone)
        assertFalse(undoManager.canUndo())
    }

    @Test
    fun testCorruptedHistoryRestorationSafelySkipped() = runBlocking {
        val validHistory = OperationHistory(id = "op-valid", actor = Actor.USER, actionType = "CREATE_TODO", targetId = "t1")
        val invalidHistory = OperationHistory(id = "op-corrupted", actor = Actor.USER, actionType = "INVALID_JSON", targetId = "t2")

        undoManager.executeCommand(MockTestCommand(validHistory))
        undoManager.executeCommand(MockTestCommand(invalidHistory))

        val newUndoManager = UndoManager(fakeDao)
        newUndoManager.initialize()
        val restoredCount = newUndoManager.restoreFromHistory { h ->
            if (h.actionType == "INVALID_JSON") {
                throw IllegalArgumentException("Corrupted JSON")
            } else {
                MockTestCommand(h)
            }
        }

        assertEquals(1, restoredCount)
        assertTrue(newUndoManager.canUndo())
    }
}
