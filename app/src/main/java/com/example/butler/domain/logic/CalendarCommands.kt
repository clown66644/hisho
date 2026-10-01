package com.example.butler.domain.logic

import com.example.butler.data.local.CalendarSyncManager
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.OperationHistory

class CreateEventCommand(
    override var history: OperationHistory,
    val event: CalendarEvent,
    private val calendarSyncManager: CalendarSyncManager,
    val allowDuplicate: Boolean = false
) : Command {

    private var generatedId: String? = null
    var detectedDuplicates: List<CalendarEvent> = emptyList()
        private set
    var duplicateDetectionFailed: Boolean = false
        private set

    override suspend fun execute(): Boolean {
        if (event.title.isBlank() || event.endTime <= event.startTime) {
            return false
        }
        val duplicateResult = calendarSyncManager.detectDuplicatesSafe(event)
        if (duplicateResult.isFailure) {
            duplicateDetectionFailed = true
            return false
        }
        val duplicates = duplicateResult.getOrDefault(emptyList())
        if (duplicates.isNotEmpty() && !allowDuplicate) {
            detectedDuplicates = duplicates
            return false
        }

        // Snapshot生成をProvider操作より前に行い、null時も安全に処理する
        val baseSnapshot = calendarEventToJson(event)

        val id = calendarSyncManager.insertEvent(event)
        return if (id != null) {
            generatedId = id
            // Provider IDで更新したsnapshotを履歴に保存
            val persistedSnapshot = org.json.JSONObject(baseSnapshot).apply {
                put("id", id)
            }.toString()
            history = history.copy(targetId = id, newStateJson = persistedSnapshot)
            true
        } else {
            false
        }
    }

    private fun calendarEventToJson(event: CalendarEvent): String {
        return org.json.JSONObject().apply {
            put("id", event.id)
            put("googleEventId", event.googleEventId)
            put("title", event.title)
            put("startTime", event.startTime)
            put("endTime", event.endTime)
            put("location", event.location)
            put("isAllDay", event.isAllDay)
            if (event.calendarId != null) put("calendarId", event.calendarId)
            if (event.timezone != null) put("timezone", event.timezone)
            if (event.recurrenceRule != null) put("recurrenceRule", event.recurrenceRule)
        }.toString()
    }

    override suspend fun undo(): Boolean {
        val targetId = generatedId ?: event.id
        return calendarSyncManager.deleteEvent(targetId)
    }
}

class UpdateEventCommand(
    override val history: OperationHistory,
    val newEvent: CalendarEvent,
    val oldEvent: CalendarEvent,
    private val calendarSyncManager: CalendarSyncManager
) : Command {

    override suspend fun execute(): Boolean {
        if (newEvent.title.isBlank() || newEvent.endTime <= newEvent.startTime) {
            return false
        }
        return calendarSyncManager.updateEvent(newEvent)
    }

    override suspend fun undo(): Boolean {
        return calendarSyncManager.updateEvent(oldEvent)
    }
}

class DeleteEventCommand(
    override var history: OperationHistory,
    val deletedEvent: CalendarEvent,
    private val calendarSyncManager: CalendarSyncManager
) : Command {

    var restoredEventId: String? = null
        private set
    var failedDueToRecurrence: Boolean = false
        private set

    /**
     * 履歴復元時にCommandResolverから呼び出し、Undo済みのProvider IDを復元する
     */
    fun restoreRestoredEventId(id: String) {
        restoredEventId = id
    }

    override suspend fun execute(): Boolean {
        if (!deletedEvent.recurrenceRule.isNullOrEmpty()) {
            failedDueToRecurrence = true
            return false
        }
        return calendarSyncManager.deleteEvent(deletedEvent.id)
    }

    override suspend fun undo(): Boolean {
        val id = calendarSyncManager.insertEvent(deletedEvent, deletedEvent.calendarId)
        return if (id != null) {
            restoredEventId = id
            // Provider IDが変わった場合でも永続履歴に反映する
            val restoredSnapshot = calendarEventToJson(deletedEvent.copy(id = id))
            history = history.copy(
                newStateJson = restoredSnapshot
            )
            true
        } else {
            false
        }
    }

    override suspend fun redo(): Boolean {
        val targetId = restoredEventId ?: deletedEvent.id
        val success = calendarSyncManager.deleteEvent(targetId)
        if (success) {
            restoredEventId = null
            // 削除成功: newStateJsonをクリアして削除済み状態を永続化
            history = history.copy(newStateJson = null)
        }
        return success
    }

    private fun calendarEventToJson(event: CalendarEvent): String {
        return org.json.JSONObject().apply {
            put("id", event.id)
            put("googleEventId", event.googleEventId)
            put("title", event.title)
            put("startTime", event.startTime)
            put("endTime", event.endTime)
            put("location", event.location)
            put("isAllDay", event.isAllDay)
            if (event.calendarId != null) put("calendarId", event.calendarId)
            if (event.timezone != null) put("timezone", event.timezone)
            if (event.recurrenceRule != null) put("recurrenceRule", event.recurrenceRule)
        }.toString()
    }
}
