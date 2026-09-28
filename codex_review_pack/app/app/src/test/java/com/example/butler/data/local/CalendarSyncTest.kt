package com.example.butler.data.local

import com.example.butler.domain.logic.AiCommandConverter
import com.example.butler.domain.logic.CreateEventCommand
import com.example.butler.domain.logic.DeleteEventCommand
import com.example.butler.domain.logic.OperationPolicyManager
import com.example.butler.domain.logic.PolicyLevel
import com.example.butler.domain.logic.UndoManager
import com.example.butler.domain.logic.UpdateEventCommand
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.OperationHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarSyncTest {

    private lateinit var inMemoryEvents: MutableList<CalendarEvent>
    private lateinit var syncManager: CalendarSyncManager
    private lateinit var undoManager: UndoManager
    private lateinit var converter: AiCommandConverter

    @Before
    fun setUp() {
        inMemoryEvents = mutableListOf()
        syncManager = CalendarSyncManager(inMemoryEvents = inMemoryEvents)
        undoManager = UndoManager()
        converter = AiCommandConverter(calendarSyncManager = syncManager)
    }

    @Test
    fun testCalendarSyncManagerCrudAndDuplicateDetection() {
        val baseTime = 1770000000000L
        val event1 = CalendarEvent(
            id = "ev-1",
            title = "経営企画会議",
            startTime = baseTime,
            endTime = baseTime + 3600000L,
            location = "第1会議室"
        )

        val insertedId = syncManager.insertEvent(event1)
        assertEquals("ev-1", insertedId)
        assertEquals(1, inMemoryEvents.size)

        val events = syncManager.getEvents(baseTime - 1000, baseTime + 5000000)
        assertEquals(1, events.size)
        assertEquals("経営企画会議", events[0].title)

        val duplicateTimeEvent = CalendarEvent(
            id = "ev-2",
            title = "来客対応",
            startTime = baseTime + 1800000L,
            endTime = baseTime + 5400000L
        )
        val duplicates = syncManager.detectDuplicates(duplicateTimeEvent)
        assertEquals(1, duplicates.size)
        assertEquals("経営企画会議", duplicates[0].title)

        val updatedEvent1 = event1.copy(title = "経営企画会議 (場所変更)", location = "第2会議室")
        assertTrue(syncManager.updateEvent(updatedEvent1))
        assertEquals("経営企画会議 (場所変更)", inMemoryEvents[0].title)
        assertEquals("第2会議室", inMemoryEvents[0].location)

        assertTrue(syncManager.deleteEvent("ev-1"))
        assertEquals(0, inMemoryEvents.size)
    }

    @Test
    fun testCalendarCommandsExecutionAndUndo() = runBlocking {
        val baseTime = 1770000000000L
        val event = CalendarEvent(
            id = "ev-cmd-1",
            title = "週次定例",
            startTime = baseTime,
            endTime = baseTime + 3600000L
        )

        val createHistory = OperationHistory(
            id = "op-ev-create",
            actor = Actor.USER,
            actionType = "CREATE_EVENT",
            targetId = event.id
        )

        val createCmd = CreateEventCommand(createHistory, event, syncManager)

        assertTrue(undoManager.executeCommand(createCmd))
        assertEquals(1, inMemoryEvents.size)

        assertTrue(undoManager.undoLastCommand())
        assertEquals(0, inMemoryEvents.size)

        assertTrue(undoManager.redoNextCommand())
        assertEquals(1, inMemoryEvents.size)

        val updatedEvent = event.copy(title = "週次定例 (時間変更)", endTime = baseTime + 7200000L)
        val updateHistory = OperationHistory(
            id = "op-ev-update",
            actor = Actor.USER,
            actionType = "UPDATE_EVENT",
            targetId = event.id
        )
        val updateCmd = UpdateEventCommand(updateHistory, updatedEvent, event, syncManager)

        assertTrue(undoManager.executeCommand(updateCmd))
        assertEquals("週次定例 (時間変更)", inMemoryEvents[0].title)

        assertTrue(undoManager.undoLastCommand())
        assertEquals("週次定例", inMemoryEvents[0].title)

        val deleteHistory = OperationHistory(
            id = "op-ev-delete",
            actor = Actor.USER,
            actionType = "DELETE_EVENT",
            targetId = event.id
        )
        val deleteCmd = DeleteEventCommand(deleteHistory, event, syncManager)

        assertTrue(undoManager.executeCommand(deleteCmd))
        assertEquals(0, inMemoryEvents.size)

        assertTrue(undoManager.undoLastCommand())
        assertEquals(1, inMemoryEvents.size)
        assertEquals("週次定例", inMemoryEvents[0].title)
    }

    @Test
    fun testAiJsonToCalendarCommandsAndPolicy() = runBlocking {
        val baseTime = 1770000000000L
        val createJson = """
        {
            "operationId": "op-ai-ev-1",
            "actionType": "CREATE_EVENT",
            "payload": {
                "title": "ランチミーティング",
                "startTime": $baseTime,
                "endTime": ${baseTime + 3600000L},
                "location": "カフェ"
            }
        }
        """.trimIndent()

        val command = converter.convertJsonToCommand(createJson, Actor.AI_BUTLER)
        assertTrue(command is CreateEventCommand)
        assertEquals("AUTO_EXECUTABLE", OperationPolicyManager.evaluatePolicy("CREATE_EVENT").name)

        assertTrue(undoManager.executeCommand(command))
        assertEquals(1, inMemoryEvents.size)
        assertEquals("ランチミーティング", inMemoryEvents[0].title)

        assertEquals(PolicyLevel.CONFIRMATION_REQUIRED, OperationPolicyManager.evaluatePolicy("UPDATE_EVENT"))
        assertEquals(PolicyLevel.CONFIRMATION_REQUIRED, OperationPolicyManager.evaluatePolicy("DELETE_EVENT"))

        val invalidTimeJson = """
        {
            "operationId": "op-ai-ev-invalid",
            "actionType": "CREATE_EVENT",
            "payload": {
                "title": "無効な時間",
                "startTime": $baseTime,
                "endTime": $baseTime
            }
        }
        """.trimIndent()

        var caught = false
        try {
            converter.convertJsonToCommand(invalidTimeJson, Actor.AI_BUTLER)
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue(caught)
    }
}
