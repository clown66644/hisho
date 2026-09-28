package com.example.butler.ui

import com.example.butler.data.local.OperationTransactionRunner
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.domain.logic.AiCommandConverter
import com.example.butler.domain.logic.UndoManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainUiControllerTest {
    private class FakeStore : TodoDao, OperationHistoryDao, OperationTransactionRunner {
        val todos = linkedMapOf<String, TodoEntity>()
        val histories = linkedMapOf<String, OperationHistoryEntity>()
        private val mutex = Mutex()

        override suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
            val todoSnapshot = LinkedHashMap(todos)
            val historySnapshot = LinkedHashMap(histories)
            try {
                block()
            } catch (error: Exception) {
                todos.clear()
                todos.putAll(todoSnapshot)
                histories.clear()
                histories.putAll(historySnapshot)
                throw error
            }
        }

        override suspend fun getAllTodos(): List<TodoEntity> = todos.values.toList()
        override suspend fun getTodoById(id: String): TodoEntity? = todos[id]
        override suspend fun upsertTodo(todo: TodoEntity) {
            todos[todo.id] = todo
        }

        override suspend fun insertTodo(todo: TodoEntity): Long {
            check(!todos.containsKey(todo.id))
            todos[todo.id] = todo
            return todos.size.toLong()
        }

        override suspend fun updateTodo(todo: TodoEntity): Int =
            if (todos.replace(todo.id, todo) != null) 1 else 0

        override suspend fun deleteTodo(todo: TodoEntity): Int =
            if (todos.remove(todo.id) != null) 1 else 0

        override suspend fun getAllHistories(): List<OperationHistoryEntity> =
            histories.values.sortedBy { it.operationOrder }

        override suspend fun getHistoryById(id: String): OperationHistoryEntity? = histories[id]

        override suspend fun insertHistory(history: OperationHistoryEntity): Long {
            if (histories.containsKey(history.id)) return -1
            histories[history.id] = history
            return histories.size.toLong()
        }

        override suspend fun updateHistory(history: OperationHistoryEntity): Int =
            if (histories.replace(history.id, history) != null) 1 else 0

        override suspend fun getNextOperationOrder(): Long =
            (histories.values.maxOfOrNull { it.operationOrder } ?: 0L) + 1L

        override suspend fun invalidateRedoHistories(updatedAt: Long): Int {
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
    }

    private lateinit var store: FakeStore
    private lateinit var controller: MainUiController
    private var now = 10_000L

    @Before
    fun setUp() {
        store = FakeStore()
        controller = newController()
    }

    @Test
    fun createUndoAndRedoAlwaysReflectDatabaseState() = runBlocking {
        controller.initialize()

        assertTrue(controller.submitAiAction(validJson("op_ui_create_01")))
        assertEquals(1, store.todos.size)
        assertEquals(store.todos.keys.single(), controller.state.value.todos.single().id)
        assertTrue(controller.state.value.canUndo)

        assertTrue(controller.undo())
        assertTrue(store.todos.isEmpty())
        assertTrue(controller.state.value.todos.isEmpty())
        assertTrue(controller.state.value.canRedo)

        assertTrue(controller.redo())
        assertEquals(1, store.todos.size)
        assertEquals(store.todos.keys.single(), controller.state.value.todos.single().id)
    }

    @Test
    fun duplicateOperationDoesNotDuplicateUiCard() = runBlocking {
        controller.initialize()
        val request = validJson("op_ui_duplicate")

        assertTrue(controller.submitAiAction(request))
        assertTrue(controller.submitAiAction(request))

        assertEquals(1, store.todos.size)
        assertEquals(1, controller.state.value.todos.size)
    }

    @Test
    fun restartRestoresUndoTargetFromEncryptedStoreContract() = runBlocking {
        controller.initialize()
        assertTrue(controller.submitAiAction(validJson("op_ui_restart_1")))

        val restored = newController()
        restored.initialize()

        assertEquals(1, restored.state.value.todos.size)
        assertTrue(restored.state.value.canUndo)
        assertTrue(restored.undo())
        assertFalse(store.todos.isNotEmpty())
        assertTrue(restored.state.value.todos.isEmpty())
    }

    private fun newController(): MainUiController {
        val converter = AiCommandConverter(store)
        val manager = UndoManager(store, store) { ++now }
        return MainUiController(store, converter, manager)
    }

    private fun validJson(operationId: String) = """
        {
          "operationId":"$operationId",
          "actionType":"CREATE_TODO",
          "payload":{
            "title":"正式UIテスト",
            "detail":"暗号化DB同期",
            "dueDate":null,
            "estimatedMinutes":20,
            "isHealthOrSafety":false,
            "financialImpact":2,
            "workImpact":3,
            "mentalLoad":2
          },
          "rationale":"UI回帰試験"
        }
    """.trimIndent()
}
