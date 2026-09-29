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
    fun testDetectDuplicatesInvalidDurationReturnsEmpty() {
        val baseTime = 1770000000000L
        val event1 = CalendarEvent(
            id = "ev-1",
            title = "既存予定",
            startTime = baseTime,
            endTime = baseTime + 3600000L
        )
        syncManager.insertEvent(event1)

        val invalidEvent = CalendarEvent(
            id = "ev-invalid",
            title = "既存予定",
            startTime = baseTime + 1000L,
            endTime = baseTime
        )
        val duplicates = syncManager.detectDuplicates(invalidEvent)
        assertTrue(duplicates.isEmpty())
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

    @Test
    fun testCreateEventCommandDuplicateCheckBlocks() = runBlocking {
        val baseTime = 1770000000000L
        val originalEvent = CalendarEvent(
            id = "ev-dup-orig",
            title = "重複テスト会議",
            startTime = baseTime,
            endTime = baseTime + 3600000L
        )
        syncManager.insertEvent(originalEvent)

        val duplicateEvent = CalendarEvent(
            id = "ev-dup-new",
            title = "重複テスト会議",
            startTime = baseTime + 1800000L,
            endTime = baseTime + 5400000L
        )
        val history = OperationHistory(
            id = "op-dup-create",
            actor = Actor.USER,
            actionType = "CREATE_EVENT",
            targetId = duplicateEvent.id
        )

        // allowDuplicate = false の場合は重複検知で実行拒否される
        val cmd = CreateEventCommand(history, duplicateEvent, syncManager, allowDuplicate = false)
        assertFalse(cmd.execute())
        assertEquals(1, cmd.detectedDuplicates.size)
        assertEquals(1, inMemoryEvents.size)

        // allowDuplicate = true の場合は作成可能
        val cmdAllowed = CreateEventCommand(history, duplicateEvent, syncManager, allowDuplicate = true)
        assertTrue(cmdAllowed.execute())
        assertEquals(2, inMemoryEvents.size)
    }

    @Test
    fun testSnapshotUpdateAndDeleteWithUndo() = runBlocking {
        val baseTime = 1770000000000L
        val event = CalendarEvent(
            id = "ev-snapshot-target",
            title = "元々の重要な予定",
            startTime = baseTime,
            endTime = baseTime + 3600000L,
            location = "本社6F"
        )
        syncManager.insertEvent(event)

        // 1. Snapshot Update: 翌月の日時へ移動（従来の範囲外検索バグを検証）
        val nextMonthTime = baseTime + 86400000L * 30
        val updateJson = """
        {
            "operationId": "op-snapshot-update",
            "actionType": "UPDATE_EVENT",
            "payload": {
                "eventId": "ev-snapshot-target",
                "title": "日時変更後の重要な予定",
                "startTime": $nextMonthTime,
                "endTime": ${nextMonthTime + 3600000L},
                "location": "別館2F"
            }
        }
        """.trimIndent()

        val updateCmd = converter.convertJsonToCommand(updateJson, Actor.USER)
        assertTrue(updateCmd is UpdateEventCommand)
        assertEquals("元々の重要な予定", (updateCmd as UpdateEventCommand).oldEvent.title)
        assertEquals("本社6F", updateCmd.oldEvent.location)

        assertTrue(undoManager.executeCommand(updateCmd))
        assertEquals("日時変更後の重要な予定", inMemoryEvents[0].title)

        // Undo すると元の日時・場所・タイトルに完全に復元される
        assertTrue(undoManager.undoLastCommand())
        assertEquals("元々の重要な予定", inMemoryEvents[0].title)
        assertEquals("本社6F", inMemoryEvents[0].location)
        assertEquals(baseTime, inMemoryEvents[0].startTime)

        // 2. Snapshot Delete: 実データをProviderから取得して削除
        val deleteJson = """
        {
            "operationId": "op-snapshot-delete",
            "actionType": "DELETE_EVENT",
            "payload": {
                "eventId": "ev-snapshot-target"
            }
        }
        """.trimIndent()

        val deleteCmd = converter.convertJsonToCommand(deleteJson, Actor.USER)
        assertTrue(deleteCmd is DeleteEventCommand)
        assertEquals("元々の重要な予定", (deleteCmd as DeleteEventCommand).deletedEvent.title)

        assertTrue(undoManager.executeCommand(deleteCmd))
        assertEquals(0, inMemoryEvents.size)

        // Undo すると元データで再登録される
        assertTrue(undoManager.undoLastCommand())
        assertEquals(1, inMemoryEvents.size)
        assertEquals("元々の重要な予定", inMemoryEvents[0].title)
    }

    @Test
    fun testNonExistentEventUpdateThrowsNoSuchElementException() {
        val nonExistentJson = """
        {
            "operationId": "op-not-found",
            "actionType": "UPDATE_EVENT",
            "payload": {
                "eventId": "unknown-event-id",
                "title": "存在しない予定",
                "startTime": 1770000000000,
                "endTime": 1770003600000
            }
        }
        """.trimIndent()

        var caught = false
        try {
            converter.convertJsonToCommand(nonExistentJson, Actor.USER)
        } catch (e: NoSuchElementException) {
            caught = true
            assertTrue(e.message!!.contains("見つかりません"))
        }
        assertTrue(caught)
    }

    @Test
    fun testDeleteEventCommandUndoAndRedo() = runBlocking {
        val customSyncManager = object : CalendarSyncManager(null, inMemoryEvents) {
            override fun insertEvent(event: CalendarEvent, calendarId: Long?): String? {
                val newId = "new-" + java.util.UUID.randomUUID().toString().substring(0, 5)
                val newEvent = event.copy(id = newId)
                inMemoryEvents?.add(newEvent)
                return newId
            }
            override fun deleteEvent(eventId: String): Boolean {
                return inMemoryEvents?.removeIf { it.id == eventId } ?: false
            }
        }
        val baseTime = 1770000000000L
        val event = CalendarEvent(
            id = "ev-del-redo",
            title = "削除Redoの件",
            startTime = baseTime,
            endTime = baseTime + 3600000L
        )
        inMemoryEvents.add(event)
        
        val history = OperationHistory(
            id = "op-del-redo",
            actor = Actor.USER,
            actionType = "DELETE_EVENT",
            targetId = event.id
        )
        val command = DeleteEventCommand(history, event, customSyncManager)
        
        val execResult = command.execute()
        assertTrue(execResult)
        assertFalse(inMemoryEvents.any { it.id == event.id })
        
        val undoResult = command.undo()
        assertTrue(undoResult)
        
        val restoredId = command.restoredEventId
        assertTrue(restoredId != null && restoredId != event.id)
        assertTrue(inMemoryEvents.any { it.id == restoredId })
        
        val redoResult = command.redo()
        assertTrue(redoResult)
        assertFalse(inMemoryEvents.any { it.id == restoredId })
    }

    @Test
    fun testCreateEventCommandFailClosedOnSafeDuplicateError() = runBlocking {
        // SafeDuplicate で例外を投げる SyncManager をエミュレート
        val faultySyncManager = object : CalendarSyncManager(inMemoryEvents = mutableListOf()) {
            override fun detectDuplicatesSafe(event: CalendarEvent): Result<List<CalendarEvent>> {
                return Result.failure(IllegalStateException("Provider unavailable"))
            }
        }
        val event = CalendarEvent(
            id = "ev-safe-fail",
            title = "Providerエラー時の予定",
            startTime = 1770000000000L,
            endTime = 1770003600000L
        )
        val history = OperationHistory(
            id = "op-safe-fail",
            actor = Actor.USER,
            actionType = "CREATE_EVENT",
            targetId = event.id
        )
        val cmd = CreateEventCommand(history, event, faultySyncManager)
        val success = cmd.execute()
        assertFalse(success)
        assertTrue(cmd.duplicateDetectionFailed)
    }

    @Test
    fun testWritableCalendarIdReturnsNullWhenNoCalendarFound() {
        val noCalendarManager = CalendarSyncManager()
        // カレンダープロバイダが存在しない/カレンダー未登録環境では 1L へのフォールバックではなく null を返すこと
        val calendarId = noCalendarManager.getWritableCalendarId()
        assertEquals(null, calendarId)
    }
}
