package com.example.butler.domain.logic

import com.example.butler.data.local.CalendarSyncManager
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.OperationHistory

class CreateEventCommand(
    override val history: OperationHistory,
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
        val id = calendarSyncManager.insertEvent(event)
        return if (id != null) {
            generatedId = id
            true
        } else {
            false
        }
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
    override val history: OperationHistory,
    val deletedEvent: CalendarEvent,
    private val calendarSyncManager: CalendarSyncManager
) : Command {

    var restoredEventId: String? = null
        private set

    override suspend fun execute(): Boolean {
        return calendarSyncManager.deleteEvent(deletedEvent.id)
    }

    override suspend fun undo(): Boolean {
        val id = calendarSyncManager.insertEvent(deletedEvent)
        return if (id != null) {
            restoredEventId = id
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
        }
        return success
    }
}
