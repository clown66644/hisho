package com.example.butler.domain.logic

import com.example.butler.data.local.CalendarSyncManager
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.CalendarEvent
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.TodoItem
import org.json.JSONObject

class CreateTodoCommand(
    override val history: OperationHistory,
    val todo: TodoItem,
    private val todoDao: TodoDao? = null,
    private val inMemoryTodoList: MutableList<TodoItem>? = null
) : Command {

    override suspend fun execute(): Boolean {
        if (todo.title.isBlank()) return false

        if (todoDao != null) {
            todoDao.insertTodo(TodoEntity.fromDomainModel(todo))
        }
        inMemoryTodoList?.add(todo)
        return true
    }

    override suspend fun undo(): Boolean {
        if (todoDao != null) {
            val entity = todoDao.getTodoById(todo.id)
            if (entity != null) {
                todoDao.deleteTodo(entity)
            }
        }
        inMemoryTodoList?.removeIf { it.id == todo.id }
        return true
    }
}

class GenericConfirmationCommand(
    override val history: OperationHistory
) : Command {
    override suspend fun execute(): Boolean = false
    override suspend fun undo(): Boolean = false
}

class AiCommandConverter(
    private val todoDao: TodoDao? = null,
    private val inMemoryTodoList: MutableList<TodoItem>? = null,
    private val calendarSyncManager: CalendarSyncManager? = null
) {

    private val defaultCalendarManager by lazy { CalendarSyncManager() }
    private fun getCalendarManager(): CalendarSyncManager = calendarSyncManager ?: defaultCalendarManager

    fun convertJsonToCommand(
        jsonString: String,
        actor: Actor = Actor.AI_BUTLER
    ): Command {
        val root = JSONObject(jsonString)

        val operationId = root.optString("operationId", "")
        if (operationId.isBlank()) {
            throw IllegalArgumentException("operationId は必須です。")
        }

        val actionType = root.optString("actionType", "")
        if (actionType.isBlank()) {
            throw IllegalArgumentException("actionType は必須です。")
        }

        // 1. セキュリティポリシー検証 (禁止操作は即座に ForbiddenOperationException を発生)
        OperationPolicyManager.assertOperationAllowed(actionType)

        val payloadObj = root.optJSONObject("payload")
            ?: throw IllegalArgumentException("payload は必須オブジェクトです。")

        return when (actionType.uppercase()) {
            "CREATE_TODO" -> parseCreateTodo(operationId, actionType, payloadObj, actor, jsonString)
            "CREATE_EVENT" -> parseCreateEvent(operationId, actionType, payloadObj, actor, jsonString)
            "UPDATE_EVENT" -> {
                val eventId = payloadObj.optString("eventId", "").trim()
                val startTime = payloadObj.optLong("startTime", -1L)
                val endTime = payloadObj.optLong("endTime", -1L)
                if (eventId.isNotBlank() && startTime >= 946684800000L && endTime > startTime) {
                    parseUpdateEvent(operationId, actionType, payloadObj, actor, jsonString)
                } else {
                    val history = OperationHistory(
                        id = operationId,
                        actor = actor,
                        actionType = actionType,
                        targetId = eventId.ifBlank { "target-$operationId" },
                        newStateJson = jsonString
                    )
                    GenericConfirmationCommand(history)
                }
            }
            "DELETE_EVENT" -> {
                val eventId = payloadObj.optString("eventId", "").trim()
                if (eventId.isNotBlank()) {
                    parseDeleteEvent(operationId, actionType, payloadObj, actor, jsonString)
                } else {
                    val history = OperationHistory(
                        id = operationId,
                        actor = actor,
                        actionType = actionType,
                        targetId = "target-$operationId",
                        newStateJson = jsonString
                    )
                    GenericConfirmationCommand(history)
                }
            }
            else -> {
                val history = OperationHistory(
                    id = operationId,
                    actor = actor,
                    actionType = actionType,
                    targetId = "target-$operationId",
                    newStateJson = jsonString
                )
                GenericConfirmationCommand(history)
            }
        }
    }

    private fun parseCreateTodo(
        operationId: String,
        actionType: String,
        payload: JSONObject,
        actor: Actor,
        rawJson: String
    ): CreateTodoCommand {
        val title = payload.optString("title", "").trim()
        if (title.isBlank()) {
            throw IllegalArgumentException("ToDo タイトルを空にすることはできません。")
        }

        val dueDate: Long? = if (payload.has("dueDate") && !payload.isNull("dueDate")) {
            val dateVal = payload.getLong("dueDate")
            // 2000年1月1日 (946684800000L) より前の過去日時は異常値として拒否
            if (dateVal < 946684800000L) {
                throw IllegalArgumentException("異常な日時指定です。")
            }
            dateVal
        } else {
            null
        }

        val detail: String? = if (payload.has("detail") && !payload.isNull("detail")) {
            payload.getString("detail")
        } else {
            null
        }

        val estimatedMinutes: Int? = if (payload.has("estimatedMinutes") && !payload.isNull("estimatedMinutes")) {
            val mins = payload.getInt("estimatedMinutes")
            if (mins < 0) {
                throw IllegalArgumentException("所要時間に負の数値は指定できません。")
            }
            if (mins > 1440) {
                throw IllegalArgumentException("所要時間は最大24時間(1440分)までです。")
            }
            mins
        } else {
            null
        }

        val isHealthOrSafety = payload.optBoolean("isHealthOrSafety", false)
        val financialImpact = payload.optInt("financialImpact", 1).coerceIn(1, 5)
        val workImpact = payload.optInt("workImpact", 1).coerceIn(1, 5)
        val mentalLoad = payload.optInt("mentalLoad", 1).coerceIn(1, 5)

        val todo = TodoItem(
            title = title,
            detail = detail,
            dueDate = dueDate,
            estimatedMinutes = estimatedMinutes,
            isHealthOrSafety = isHealthOrSafety,
            financialImpact = financialImpact,
            workImpact = workImpact,
            mentalLoad = mentalLoad
        )

        val history = OperationHistory(
            id = operationId,
            actor = actor,
            actionType = actionType,
            targetId = todo.id,
            newStateJson = rawJson
        )

        return CreateTodoCommand(
            history = history,
            todo = todo,
            todoDao = todoDao,
            inMemoryTodoList = inMemoryTodoList
        )
    }

    private fun parseCreateEvent(
        operationId: String,
        actionType: String,
        payload: JSONObject,
        actor: Actor,
        rawJson: String
    ): CreateEventCommand {
        val title = payload.optString("title", "").trim()
        if (title.isBlank()) {
            throw IllegalArgumentException("予定タイトルを空にすることはできません。")
        }

        val startTime = payload.optLong("startTime", -1L)
        val endTime = payload.optLong("endTime", -1L)

        if (startTime < 946684800000L || endTime < 946684800000L) {
            throw IllegalArgumentException("異常な日時指定です。")
        }
        if (endTime <= startTime) {
            throw IllegalArgumentException("終了日時は開始日時より後である必要があります。")
        }

        val location = if (payload.has("location") && !payload.isNull("location")) {
            payload.getString("location")
        } else null

        val isAllDay = payload.optBoolean("isAllDay", false)

        val event = CalendarEvent(
            title = title,
            startTime = startTime,
            endTime = endTime,
            location = location,
            isAllDay = isAllDay
        )

        val history = OperationHistory(
            id = operationId,
            actor = actor,
            actionType = actionType,
            targetId = event.id,
            newStateJson = rawJson
        )

        return CreateEventCommand(
            history = history,
            event = event,
            calendarSyncManager = getCalendarManager()
        )
    }

    private fun parseUpdateEvent(
        operationId: String,
        actionType: String,
        payload: JSONObject,
        actor: Actor,
        rawJson: String
    ): UpdateEventCommand {
        val eventId = payload.optString("eventId", "").trim()
        if (eventId.isBlank()) {
            throw IllegalArgumentException("eventId は必須です。")
        }

        val existing = getCalendarManager().getEventById(eventId)
            ?: throw NoSuchElementException("更新対象の予定 (ID: $eventId) がカレンダープロバイダに見つかりません。")

        val title = payload.optString("title", existing.title).trim()
        if (title.isBlank()) {
            throw IllegalArgumentException("予定タイトルを空にすることはできません。")
        }

        val startTime = if (payload.has("startTime")) payload.getLong("startTime") else existing.startTime
        val endTime = if (payload.has("endTime")) payload.getLong("endTime") else existing.endTime

        if (startTime < 946684800000L || endTime < 946684800000L) {
            throw IllegalArgumentException("異常な日時指定です。")
        }
        if (endTime <= startTime) {
            throw IllegalArgumentException("終了日時は開始日時より後である必要があります。")
        }

        val location = if (payload.has("location") && !payload.isNull("location")) {
            payload.getString("location")
        } else existing.location

        val isAllDay = if (payload.has("isAllDay")) payload.getBoolean("isAllDay") else existing.isAllDay

        val newEvent = existing.copy(
            title = title,
            startTime = startTime,
            endTime = endTime,
            location = location,
            isAllDay = isAllDay
        )

        val history = OperationHistory(
            id = operationId,
            actor = actor,
            actionType = actionType,
            targetId = eventId,
            newStateJson = rawJson
        )

        return UpdateEventCommand(
            history = history,
            newEvent = newEvent,
            oldEvent = existing,
            calendarSyncManager = getCalendarManager()
        )
    }

    private fun parseDeleteEvent(
        operationId: String,
        actionType: String,
        payload: JSONObject,
        actor: Actor,
        rawJson: String
    ): DeleteEventCommand {
        val eventId = payload.optString("eventId", "").trim()
        if (eventId.isBlank()) {
            throw IllegalArgumentException("eventId は必須です。")
        }

        val existing = getCalendarManager().getEventById(eventId)
            ?: throw NoSuchElementException("削除対象の予定 (ID: $eventId) がカレンダープロバイダに見つかりません。")

        val history = OperationHistory(
            id = operationId,
            actor = actor,
            actionType = actionType,
            targetId = eventId,
            newStateJson = rawJson
        )

        return DeleteEventCommand(
            history = history,
            deletedEvent = existing,
            calendarSyncManager = getCalendarManager()
        )
    }
}
