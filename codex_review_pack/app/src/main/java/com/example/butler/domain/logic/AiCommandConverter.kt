package com.example.butler.domain.logic

import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.data.remote.model.AiActionJsonSchema
import com.example.butler.data.remote.model.AiActionRequest
import com.example.butler.data.remote.model.AiActionType
import com.example.butler.data.remote.model.AiTodoPayload
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.OperationStatus
import com.example.butler.domain.model.TodoItem
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class CreateTodoCommand internal constructor(
    override val history: OperationHistory,
    val todo: TodoItem,
    private val todoDao: TodoDao,
) : Command {
    override suspend fun execute(): OperationStatus = try {
        todoDao.insertTodo(TodoEntity.fromDomainModel(todo))
        OperationStatus.SUCCESS
    } catch (_: Exception) {
        OperationStatus.FAILED
    }

    override suspend fun redo(): OperationStatus = execute()

    override suspend fun undo(): OperationStatus = try {
        todoDao.getTodoById(todo.id)?.let { entity ->
            if (todoDao.deleteTodo(entity) != 1) return OperationStatus.FAILED
        }
        OperationStatus.SUCCESS
    } catch (_: Exception) {
        OperationStatus.FAILED
    }
}

class AiCommandConverter internal constructor(
    private val todoDao: TodoDao,
    private val idGenerator: (String) -> String = ::stableTodoId,
) {
    constructor(database: AppDatabase) : this(database.todoDao())

    fun convertJsonToCommand(
        jsonString: String,
        actor: Actor = Actor.AI_BUTLER,
    ): Command {
        val request = parseRequest(jsonString)
        OperationPolicyManager.requireAutoExecutable(request.actionType)
        return createTodoCommand(
            request = request,
            history = OperationHistory(
                id = request.operationId,
                actor = actor,
                actionType = request.actionType.name,
                targetId = idGenerator(request.operationId),
                aiInterpretation = request.rationale,
                newStateJson = jsonString,
            ),
        )
    }

    /** 永続履歴からは元のtargetIdを必ず再利用し、Undo対象を変えない。 */
    fun restoreCommand(history: OperationHistory): Command? {
        if (history.newStateJson == null) return null
        val request = parseRequest(history.newStateJson)
        if (request.operationId != history.id || request.actionType.name != history.actionType) {
            return null
        }
        OperationPolicyManager.requireAutoExecutable(request.actionType)
        return createTodoCommand(request, history)
    }

    internal fun parseRequest(jsonString: String): AiActionRequest {
        require(jsonString.length <= MAX_ACTION_JSON_CHARS) { "AI操作JSONが長すぎます。" }
        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            throw IllegalArgumentException("AI操作JSONが不正です。")
        }
        requireExactKeys(root, ROOT_KEYS, "ルート")

        val operationId = root.optString("operationId", "")
        require(AiActionJsonSchema.OPERATION_ID.matches(operationId)) {
            "operationIdの形式が不正です。"
        }
        val actionType = try {
            AiActionType.valueOf(root.getString("actionType"))
        } catch (_: Exception) {
            throw IllegalArgumentException("actionTypeが不正です。")
        }
        val payload = root.optJSONObject("payload")
            ?: throw IllegalArgumentException("payloadはオブジェクトである必要があります。")
        requireExactKeys(payload, PAYLOAD_KEYS, "payload")

        val title = payload.requireString("title").trim()
        require(title.isNotEmpty() && title.length <= MAX_TITLE_CHARS) {
            "ToDoタイトルの長さが不正です。"
        }
        val detail = payload.nullableString("detail")?.also {
            require(it.length <= MAX_DETAIL_CHARS) { "詳細が長すぎます。" }
        }
        val dueDate = payload.nullableLong("dueDate")?.also {
            require(it >= 0L) { "日時が不正です。" }
        }
        val estimatedMinutes = payload.nullableInt("estimatedMinutes")?.also {
            require(it in 1..MAX_ESTIMATED_MINUTES) { "所要時間が不正です。" }
        }
        val rationale = root.nullableString("rationale")?.also {
            require(it.length <= MAX_RATIONALE_CHARS) { "実行理由が長すぎます。" }
        }

        return AiActionRequest(
            operationId = operationId,
            actionType = actionType,
            payload = AiTodoPayload(
                title = title,
                detail = detail,
                dueDate = dueDate,
                estimatedMinutes = estimatedMinutes,
                isHealthOrSafety = payload.requireBoolean("isHealthOrSafety"),
                financialImpact = payload.requireFactor("financialImpact"),
                workImpact = payload.requireFactor("workImpact"),
                mentalLoad = payload.requireFactor("mentalLoad"),
            ),
            rationale = rationale,
        )
    }

    private fun createTodoCommand(
        request: AiActionRequest,
        history: OperationHistory,
    ): CreateTodoCommand {
        require(request.actionType == AiActionType.CREATE_TODO)
        val payload = request.payload
        return CreateTodoCommand(
            history = history,
            todo = TodoItem(
                id = history.targetId,
                title = payload.title,
                detail = payload.detail,
                dueDate = payload.dueDate,
                estimatedMinutes = payload.estimatedMinutes,
                isHealthOrSafety = payload.isHealthOrSafety,
                financialImpact = payload.financialImpact,
                workImpact = payload.workImpact,
                mentalLoad = payload.mentalLoad,
            ),
            todoDao = todoDao,
        )
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>, label: String) {
        val actual = value.keys().asSequence().toSet()
        require(actual == expected) { "${label}のフィールドが不正です。" }
    }

    private fun JSONObject.requireString(name: String): String =
        get(name).takeIf { it is String } as? String
            ?: throw IllegalArgumentException("${name}は文字列である必要があります。")

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else requireString(name)

    private fun JSONObject.requireBoolean(name: String): Boolean =
        get(name) as? Boolean
            ?: throw IllegalArgumentException("${name}は真偽値である必要があります。")

    private fun JSONObject.nullableLong(name: String): Long? {
        if (isNull(name)) return null
        val value = get(name)
        require(value is Long || value is Int) { "${name}は整数である必要があります。" }
        return (value as Number).toLong()
    }

    private fun JSONObject.nullableInt(name: String): Int? {
        val value = nullableLong(name) ?: return null
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "${name}が範囲外です。" }
        return value.toInt()
    }

    private fun JSONObject.requireFactor(name: String): Int =
        nullableInt(name)?.also {
            require(it in 1..5) { "${name}は1〜5で指定してください。" }
        } ?: throw IllegalArgumentException("${name}は必須です。")

    private companion object {
        private fun stableTodoId(operationId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(
                "CREATE_TODO:$operationId".toByteArray(StandardCharsets.UTF_8),
            )
            val suffix = digest.take(16).joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            return "ai-todo-$suffix"
        }

        val ROOT_KEYS = setOf("operationId", "actionType", "payload", "rationale")
        val PAYLOAD_KEYS = setOf(
            "title",
            "detail",
            "dueDate",
            "estimatedMinutes",
            "isHealthOrSafety",
            "financialImpact",
            "workImpact",
            "mentalLoad",
        )
        const val MAX_ACTION_JSON_CHARS = 10_000
        const val MAX_TITLE_CHARS = 200
        const val MAX_DETAIL_CHARS = 2_000
        const val MAX_RATIONALE_CHARS = 1_000
        const val MAX_ESTIMATED_MINUTES = 30 * 24 * 60
    }
}
