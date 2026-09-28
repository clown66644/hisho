package com.example.butler.domain.logic

import com.example.butler.data.local.OperationTransactionRunner
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.data.remote.model.AiActionType
import com.example.butler.domain.model.OperationStatus
import com.example.butler.domain.model.TodoItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiStructuredActionTest {
    private class FakeStore : TodoDao, OperationHistoryDao, OperationTransactionRunner {
        val todos = linkedMapOf<String, TodoEntity>()
        val histories = linkedMapOf<String, OperationHistoryEntity>()
        private val transactionMutex = Mutex()

        override suspend fun <T> run(block: suspend () -> T): T =
            transactionMutex.withLock {
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
            check(!todos.containsKey(todo.id)) { "duplicate todo id" }
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
            if (histories.containsKey(history.id)) return -1L
            histories[history.id] = history
            return histories.size.toLong()
        }

        override suspend fun updateHistory(history: OperationHistoryEntity): Int =
            if (histories.replace(history.id, history) != null) 1 else 0

        override suspend fun getNextOperationOrder(): Long =
            (histories.values.maxOfOrNull { it.operationOrder } ?: 0L) + 1

        override suspend fun invalidateRedoHistories(updatedAt: Long): Int {
            var changed = 0
            histories.replaceAll { _, history ->
                if (history.isUndone && history.isRedoable) {
                    changed++
                    history.copy(isRedoable = false, updatedAt = updatedAt)
                } else {
                    history
                }
            }
            return changed
        }
    }

    private lateinit var store: FakeStore
    private lateinit var converter: AiCommandConverter
    private lateinit var manager: UndoManager
    private var currentTime = 1_000L

    @Before
    fun setUp() {
        store = FakeStore()
        converter = AiCommandConverter(store)
        manager = UndoManager(store, store) { ++currentTime }
    }

    @Test
    fun policyRequiresConfirmationAndBlocksForbiddenActions() {
        assertEquals(
            PolicyLevel.AUTO_EXECUTABLE,
            OperationPolicyManager.evaluatePolicy(AiActionType.CREATE_TODO),
        )
        assertThrows(ConfirmationRequiredException::class.java) {
            OperationPolicyManager.requireAutoExecutable(AiActionType.DELETE_TODO)
        }
        assertThrows(ForbiddenOperationException::class.java) {
            OperationPolicyManager.requireAutoExecutable(AiActionType.EXECUTE_PAYMENT)
        }
    }

    @Test
    fun validCreateTodoExecutesAndUndoesThroughPersistentManager() = runBlocking {
        val command = converter.convertJsonToCommand(validJson("op_create_001")) as CreateTodoCommand
        val targetId = command.todo.id

        assertTrue(manager.executeCommand(command))
        assertEquals("書類提出", store.todos[targetId]?.title)
        assertEquals(OperationStatus.SUCCESS.name, store.histories["op_create_001"]?.status)

        assertTrue(manager.undoLastCommand())
        assertFalse(store.todos.containsKey(targetId))
        assertTrue(store.histories["op_create_001"]!!.isUndone)
    }

    @Test
    fun duplicateOperationIdDoesNotCreateSecondTodo() = runBlocking {
        val first =
            converter.convertJsonToCommand(validJson("op_duplicate_01")) as CreateTodoCommand
        val second =
            converter.convertJsonToCommand(validJson("op_duplicate_01")) as CreateTodoCommand

        assertEquals(first.todo.id, second.todo.id)
        assertTrue(manager.executeCommand(first))
        assertTrue(manager.executeCommand(second))

        assertEquals(1, store.todos.size)
        assertTrue(store.todos.containsKey(first.todo.id))
    }

    @Test
    fun createCollisionFailsWithoutOverwritingExistingTodo() = runBlocking {
        val command =
            converter.convertJsonToCommand(validJson("op_collision_01")) as CreateTodoCommand
        val targetId = command.todo.id
        store.todos[targetId] = TodoEntity.fromDomainModel(
            TodoItem(id = targetId, title = "既存データ"),
        )

        assertFalse(manager.executeCommand(command))
        assertEquals("既存データ", store.todos[targetId]?.title)
        assertEquals(OperationStatus.FAILED.name, store.histories["op_collision_01"]?.status)
    }

    @Test
    fun restoredCommandUsesPersistedTargetId() = runBlocking {
        val command = converter.convertJsonToCommand(validJson("op_restore_001")) as CreateTodoCommand
        val targetId = command.todo.id
        assertTrue(manager.executeCommand(command))
        val restored = UndoManager(store, store) { 2_000L }

        assertEquals(1, restored.restoreFromHistory(converter::restoreCommand))
        assertTrue(restored.undoLastCommand())

        assertFalse(store.todos.containsKey(targetId))
    }

    @Test
    fun confirmationRequiredAndForbiddenJsonNeverCreateCommands() {
        assertThrows(ConfirmationRequiredException::class.java) {
            converter.convertJsonToCommand(validJson("op_confirm_001", "DELETE_TODO"))
        }
        assertThrows(ForbiddenOperationException::class.java) {
            converter.convertJsonToCommand(validJson("op_forbidden_1", "EXECUTE_PAYMENT"))
        }
        assertTrue(store.todos.isEmpty())
    }

    @Test
    fun malformedTypesUnknownFieldsAndOutOfRangeValuesAreRejected() {
        val wrongType = validJson("op_wrong_type").replace(
            "\"financialImpact\":2",
            "\"financialImpact\":\"2\"",
        )
        val unknownField = validJson("op_extra_field").replace(
            "\"mentalLoad\":2",
            "\"mentalLoad\":2,\"unexpected\":true",
        )
        val outOfRange = validJson("op_bad_factor").replace(
            "\"mentalLoad\":2",
            "\"mentalLoad\":99",
        )
        val wrongBoolean = validJson("op_wrong_bool").replace(
            "\"isHealthOrSafety\":false",
            "\"isHealthOrSafety\":\"false\"",
        )

        assertThrows(IllegalArgumentException::class.java) {
            converter.convertJsonToCommand(wrongType)
        }
        assertThrows(IllegalArgumentException::class.java) {
            converter.convertJsonToCommand(unknownField)
        }
        assertThrows(IllegalArgumentException::class.java) {
            converter.convertJsonToCommand(outOfRange)
        }
        assertThrows(IllegalArgumentException::class.java) {
            converter.convertJsonToCommand(wrongBoolean)
        }
    }

    private fun validJson(
        operationId: String,
        actionType: String = "CREATE_TODO",
    ) = """
        {
          "operationId":"$operationId",
          "actionType":"$actionType",
          "payload":{
            "title":"書類提出",
            "detail":"月曜日まで",
            "dueDate":1750000000000,
            "estimatedMinutes":30,
            "isHealthOrSafety":false,
            "financialImpact":2,
            "workImpact":4,
            "mentalLoad":2
          },
          "rationale":"期限が近いため"
        }
    """.trimIndent()
}
