package com.example.butler.domain.logic

import org.json.JSONObject
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.TodoItem
import com.example.butler.domain.model.TodoStatus
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
                    val cmd = DeleteEventCommand(history, deletedEvent, calendarSyncManager)
                    // Undo済みの場合、newStateJsonに復元されたイベントのProvider IDが保存されている
                    if (history.newStateJson != null && history.isUndone) {
                        try {
                            val restoredJson = JSONObject(history.newStateJson)
                            val restoredId = restoredJson.getString("id")
                            cmd.restoreRestoredEventId(restoredId)
                        } catch (_: Exception) {
                            // newStateJsonの解析失敗は無視（deletedEvent.idにフォールバック）
                        }
                    }
                    cmd
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
            android.util.Log.e("CommandResolver", "Failed to restore command. operationId=" + history.id + ", actionType=" + history.actionType, e)
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

    fun todoToJson(todo: TodoItem): String {
        return JSONObject().apply {
            put("id", todo.id)
            put("title", todo.title)
            put("status", todo.status.name)
            put("createdAt", todo.createdAt)
            put("updatedAt", todo.updatedAt)
            if (todo.detail != null) put("detail", todo.detail)
            if (todo.dueDate != null) put("dueDate", todo.dueDate)
            if (todo.estimatedMinutes != null) put("estimatedMinutes", todo.estimatedMinutes)
            put("isHealthOrSafety", todo.isHealthOrSafety)
            put("financialImpact", todo.financialImpact)
            put("workImpact", todo.workImpact)
            put("mentalLoad", todo.mentalLoad)
            
            if (todo.memo != null) put("memo", todo.memo)
            if (todo.scheduledStartTime != null) put("scheduledStartTime", todo.scheduledStartTime)
            if (todo.scheduledEndTime != null) put("scheduledEndTime", todo.scheduledEndTime)
            put("irretrievableLoss", todo.irretrievableLoss)
            put("requiredStamina", todo.requiredStamina)
            put("isPinnedPriority", todo.isPinnedPriority)
            if (todo.pinnedPriority != null) put("pinnedPriority", todo.pinnedPriority.name)
        }.toString()
    }
    
    fun jsonToTodo(json: JSONObject): TodoItem {
        return TodoItem(
            id = json.getString("id"),
            title = json.getString("title"),
            status = try { TodoStatus.valueOf(json.getString("status")) } catch (e: Exception) { TodoStatus.UNSTARTED },
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            detail = json.optString("detail", null),
            dueDate = if (json.has("dueDate")) json.getLong("dueDate") else null,
            estimatedMinutes = if (json.has("estimatedMinutes")) json.getInt("estimatedMinutes") else null,
            isHealthOrSafety = json.getBoolean("isHealthOrSafety"),
            financialImpact = json.getInt("financialImpact"),
            workImpact = json.getInt("workImpact"),
            mentalLoad = json.getInt("mentalLoad"),
            
            memo = json.optString("memo", null),
            scheduledStartTime = if (json.has("scheduledStartTime")) json.getLong("scheduledStartTime") else null,
            scheduledEndTime = if (json.has("scheduledEndTime")) json.getLong("scheduledEndTime") else null,
            irretrievableLoss = json.optBoolean("irretrievableLoss", false),
            requiredStamina = json.optInt("requiredStamina", 1),
            isPinnedPriority = json.optBoolean("isPinnedPriority", false),
            pinnedPriority = if (json.has("pinnedPriority")) com.example.butler.domain.model.PriorityLevel.valueOf(json.getString("pinnedPriority")) else null
        )
    }
}