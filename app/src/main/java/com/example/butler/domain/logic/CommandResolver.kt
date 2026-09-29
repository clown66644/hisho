package com.example.butler.domain.logic

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
                    val newJson = JSONObject(history.newStateJson ?: return null)
                    val todo = jsonToTodo(newJson)
                    CreateTodoCommand(history, todo, todoDao, inMemoryTodoList)
                }
                "CREATE_EVENT" -> {
                    val newJson = JSONObject(history.newStateJson ?: return null)
                    val event = jsonToCalendarEvent(newJson)
                    CreateEventCommand(history, event, calendarSyncManager)
                }
                "UPDATE_EVENT" -> {
                    val oldJson = JSONObject(history.previousStateJson ?: return null)
                    val oldEvent = jsonToCalendarEvent(oldJson)
                    val newJson = JSONObject(history.newStateJson ?: return null)
                    val newEvent = jsonToCalendarEvent(newJson)
                    UpdateEventCommand(history, newEvent, oldEvent, calendarSyncManager)
                }
                "DELETE_EVENT" -> {
                    val oldJson = JSONObject(history.previousStateJson ?: return null)
                    val deletedEvent = jsonToCalendarEvent(oldJson)
                    DeleteEventCommand(history, deletedEvent, calendarSyncManager)
                }
                "COMPLETE_TODO" -> {
                    val oldJson = JSONObject(history.previousStateJson ?: return null)
                    val oldTodo = jsonToTodo(oldJson)
                    val newJson = JSONObject(history.newStateJson ?: return null)
                    val newTodo = jsonToTodo(newJson)
                    UpdateTodoCommand(history, newTodo, oldTodo, todoDao, inMemoryTodoList)
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

    private fun jsonToTodo(json: JSONObject): TodoItem {
        return TodoItem(
            id = json.getString("id"),
            title = json.getString("title"),
            status = json.getString("status"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            detail = json.optString("detail", null),
            dueDate = if (json.has("dueDate")) json.getLong("dueDate") else null,
            estimatedMinutes = if (json.has("estimatedMinutes")) json.getInt("estimatedMinutes") else null,
            isHealthOrSafety = json.getBoolean("isHealthOrSafety"),
            financialImpact = json.getInt("financialImpact"),
            workImpact = json.getInt("workImpact"),
            mentalLoad = json.getInt("mentalLoad")
        )
    }
}