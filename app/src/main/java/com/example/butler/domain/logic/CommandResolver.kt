package com.example.butler.domain.logic

// Let's create a file CommandResolver.kt in the same package to keep it clean!
import org.json.JSONObject
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.TodoItem
import com.example.butler.domain.model.OperationHistory
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.CalendarSyncManager

object CommandResolver {
    fun restoreCommand(
        history: OperationHistory,
        todoDao: TodoDao?,
        inMemoryTodoList: MutableList<TodoItem>?,
        calendarSyncManager: CalendarSyncManager
    ): Command? {
        return try {
            when (history.actionType) {
                "CREATE_TODO" -> {
                    // For create, targetId is todo.id. We can't fully rebuild the original todo from raw json without re-parsing.
                    // But we don't strictly need to undo CREATE_TODO across restarts for now, or we can just parse it from rawJson.
                    null
                }
                "CREATE_EVENT" -> {
                    null // skip for now
                }
                "UPDATE_EVENT" -> {
                    val oldJson = JSONObject(history.previousStateJson ?: return null)
                    val oldEvent = jsonToCalendarEvent(oldJson)
                    val newJson = JSONObject(history.newStateJson ?: return null)
                    // If newStateJson is raw AI command, we can't easily parse it without the existing event.
                    // Let's just return null for UPDATE_EVENT restore for now, or just do DELETE_EVENT first.
                    null
                }
                "DELETE_EVENT" -> {
                    val oldJson = JSONObject(history.previousStateJson ?: return null)
                    val deletedEvent = jsonToCalendarEvent(oldJson)
                    DeleteEventCommand(history, deletedEvent, calendarSyncManager)
                }
                "COMPLETE_TODO" -> {
                    // We need to add CompleteTodoCommand!
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonToCalendarEvent(json: JSONObject): CalendarEvent {
        return CalendarEvent(
            id = json.getString("id"),
            googleEventId = json.optString("googleEventId", null),
            title = json.getString("title"),
            startTime = json.getLong("startTime"),
            endTime = json.getLong("endTime"),
            location = json.optString("location", null),
            isAllDay = json.getBoolean("isAllDay"),
            calendarId = if (json.has("calendarId")) json.getLong("calendarId") else null,
            timezone = json.optString("timezone", null),
            recurrenceRule = json.optString("recurrenceRule", null)
        )
    }
}