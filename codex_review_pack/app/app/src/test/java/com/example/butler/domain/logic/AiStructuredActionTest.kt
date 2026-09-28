package com.example.butler.domain.logic

import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.TodoItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AiStructuredActionTest {

    private lateinit var inMemoryTodoList: MutableList<TodoItem>
    private lateinit var converter: AiCommandConverter
    private lateinit var undoManager: UndoManager

    @Before
    fun setUp() {
        inMemoryTodoList = mutableListOf()
        converter = AiCommandConverter(inMemoryTodoList = inMemoryTodoList)
        undoManager = UndoManager()
    }

    @Test
    fun testPolicyLevelsCategorization() {
        val forbiddenList = listOf(
            "SEND_MESSAGE", "EXECUTE_PAYMENT", "PAYMENT", "PURCHASE",
            "CONTRACT", "CHANGE_MEDICATION", "STOP_MEDICATION",
            "MEDICAL_DIAGNOSIS", "EXTERNAL_CONTACT"
        )
        for (action in forbiddenList) {
            assertEquals(PolicyLevel.FORBIDDEN, OperationPolicyManager.evaluatePolicy(action))
        }

        val confirmationRequiredList = listOf(
            "UPDATE_TODO", "DELETE_TODO", "UPDATE_EVENT", "DELETE_EVENT",
            "COMPLETE_MULTIPLE_TODOS", "BULK_OPERATION", "UPDATE_MEDICATION_RECORD", "UPDATE_SETTINGS"
        )
        for (action in confirmationRequiredList) {
            assertEquals(PolicyLevel.CONFIRMATION_REQUIRED, OperationPolicyManager.evaluatePolicy(action))
        }

        val autoList = listOf("CREATE_TODO", "SEARCH_INFO", "VIEW_SCHEDULE")
        for (action in autoList) {
            assertEquals(PolicyLevel.AUTO_EXECUTABLE, OperationPolicyManager.evaluatePolicy(action))
        }

        assertEquals(PolicyLevel.CONFIRMATION_REQUIRED, OperationPolicyManager.evaluatePolicy("UNKNOWN_ACTION_999"))
    }

    @Test
    fun testForbiddenOperationsBlockedByPolicy() {
        val forbiddenActions = listOf("SEND_MESSAGE", "EXECUTE_PAYMENT", "PAYMENT", "PURCHASE", "CONTRACT", "CHANGE_MEDICATION")

        for (action in forbiddenActions) {
            val json = """
            {
                "operationId": "op-forbidden-$action",
                "actionType": "$action",
                "payload": {
                    "target": "user@example.com",
                    "amount": 10000
                }
            }
            """.trimIndent()

            try {
                converter.convertJsonToCommand(json)
                fail("Should have thrown ForbiddenOperationException for action $action")
            } catch (e: ForbiddenOperationException) {
                assertTrue(e.message!!.contains("禁止された"))
            }
        }
    }

    @Test
    fun testValidCreateTodoJsonExecutionViaCommandOnly() = runBlocking {
        val validJson = """
        {
            "operationId": "op-ai-todo-1",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "書類提出",
                "detail": "月曜朝までに作成",
                "dueDate": 1750000000000,
                "estimatedMinutes": 30,
                "isHealthOrSafety": false,
                "financialImpact": 2,
                "workImpact": 4,
                "mentalLoad": 2
            }
        }
        """.trimIndent()

        val command = converter.convertJsonToCommand(validJson, Actor.AI_BUTLER)
        assertEquals("op-ai-todo-1", command.history.id)

        assertEquals(0, inMemoryTodoList.size)

        val result = undoManager.executeCommand(command)
        assertTrue(result)
        assertEquals(1, inMemoryTodoList.size)
        assertEquals("書類提出", inMemoryTodoList[0].title)

        assertTrue(undoManager.undoLastCommand())
        assertEquals(0, inMemoryTodoList.size)
    }

    @Test
    fun testStrictJsonValidationMissingFields() {
        val missingOpId = """{"actionType": "CREATE_TODO", "payload": {"title": "テスト"}}"""
        try {
            converter.convertJsonToCommand(missingOpId)
            fail("Should fail for missing operationId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("operationId"))
        }

        val missingAction = """{"operationId": "op-1", "payload": {"title": "テスト"}}"""
        try {
            converter.convertJsonToCommand(missingAction)
            fail("Should fail for missing actionType")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("actionType"))
        }

        val missingPayload = """{"operationId": "op-1", "actionType": "CREATE_TODO"}"""
        try {
            converter.convertJsonToCommand(missingPayload)
            fail("Should fail for missing payload")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("payload"))
        }
    }

    @Test
    fun testBlankTitleRejected() {
        val emptyTitleJson = """
        {
            "operationId": "op-ai-todo-2",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "   "
            }
        }
        """.trimIndent()

        try {
            converter.convertJsonToCommand(emptyTitleJson)
            fail("Should have thrown IllegalArgumentException for blank title")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("タイトル"))
        }
    }

    @Test
    fun testInvalidDateAndOutofBoundMinutesRejected() {
        val invalidDateJson = """
        {
            "operationId": "op-ai-invalid-date",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "テスト",
                "dueDate": 100000
            }
        }
        """.trimIndent()

        try {
            converter.convertJsonToCommand(invalidDateJson)
            fail("Should have thrown IllegalArgumentException for invalid date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("日時"))
        }

        val negativeMinsJson = """
        {
            "operationId": "op-ai-negative-mins",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "テスト",
                "estimatedMinutes": -15
            }
        }
        """.trimIndent()

        try {
            converter.convertJsonToCommand(negativeMinsJson)
            fail("Should have thrown IllegalArgumentException for negative estimatedMinutes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("所要時間"))
        }

        val excessiveMinsJson = """
        {
            "operationId": "op-ai-excessive-mins",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "テスト",
                "estimatedMinutes": 5000
            }
        }
        """.trimIndent()

        try {
            converter.convertJsonToCommand(excessiveMinsJson)
            fail("Should have thrown IllegalArgumentException for excessive estimatedMinutes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("1440分"))
        }
    }

    @Test
    fun testDuplicateOperationIdPrevented() = runBlocking {
        val json = """
        {
            "operationId": "op-ai-dup-check",
            "actionType": "CREATE_TODO",
            "payload": {
                "title": "重複テスト"
            }
        }
        """.trimIndent()

        val command1 = converter.convertJsonToCommand(json)
        val command2 = converter.convertJsonToCommand(json)

        assertTrue(undoManager.executeCommand(command1))
        assertFalse(undoManager.executeCommand(command2))
        assertEquals(1, inMemoryTodoList.size)
    }
}
