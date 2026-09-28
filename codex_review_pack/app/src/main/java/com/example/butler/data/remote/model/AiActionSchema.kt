package com.example.butler.data.remote.model

import org.json.JSONArray
import org.json.JSONObject

enum class AiActionType {
    CREATE_TODO,
    UPDATE_TODO,
    DELETE_TODO,
    UPDATE_EVENT,
    DELETE_EVENT,
    COMPLETE_MULTIPLE_TODOS,
    UPDATE_SETTINGS,
    SEND_MESSAGE,
    EXECUTE_PAYMENT,
    CHANGE_MEDICATION,
}

data class AiTodoPayload(
    val title: String,
    val detail: String?,
    val dueDate: Long?,
    val estimatedMinutes: Int?,
    val isHealthOrSafety: Boolean,
    val financialImpact: Int,
    val workImpact: Int,
    val mentalLoad: Int,
)

data class AiActionRequest(
    val operationId: String,
    val actionType: AiActionType,
    val payload: AiTodoPayload,
    val rationale: String?,
)

/**
 * Responses APIの`text.format`へ渡す厳格なJSON Schema。
 * 現段階では実装済みかつ自動実行可能なCREATE_TODOだけを生成対象にする。
 */
object AiActionJsonSchema {
    fun createTodoTextFormat(operationId: String): JSONObject {
        require(OPERATION_ID.matches(operationId)) { "operationIdの形式が不正です。" }
        return JSONObject().apply {
            put("type", "json_schema")
            put("name", "butler_create_todo")
            put("strict", true)
            put("schema", JSONObject().apply {
                put("type", "object")
                put("additionalProperties", false)
                put(
                    "required",
                    JSONArray(listOf("operationId", "actionType", "payload", "rationale")),
                )
                put("properties", JSONObject().apply {
                    put("operationId", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf(operationId)))
                    })
                    put("actionType", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf(AiActionType.CREATE_TODO.name)))
                    })
                    put("payload", todoPayloadSchema())
                    put("rationale", nullableType("string"))
                })
            })
        }
    }

    private fun todoPayloadSchema() = JSONObject().apply {
        put("type", "object")
        put("additionalProperties", false)
        put(
            "required",
            JSONArray(
                listOf(
                    "title",
                    "detail",
                    "dueDate",
                    "estimatedMinutes",
                    "isHealthOrSafety",
                    "financialImpact",
                    "workImpact",
                    "mentalLoad",
                )
            ),
        )
        put("properties", JSONObject().apply {
            put("title", JSONObject().apply {
                put("type", "string")
                put("minLength", 1)
                put("maxLength", 200)
            })
            put("detail", nullableType("string").apply { put("maxLength", 2_000) })
            put("dueDate", nullableType("integer"))
            put("estimatedMinutes", nullableType("integer"))
            put("isHealthOrSafety", JSONObject().put("type", "boolean"))
            put("financialImpact", boundedFactor())
            put("workImpact", boundedFactor())
            put("mentalLoad", boundedFactor())
        })
    }

    private fun nullableType(type: String) =
        JSONObject().put("type", JSONArray(listOf(type, "null")))

    private fun boundedFactor() = JSONObject().apply {
        put("type", "integer")
        put("minimum", 1)
        put("maximum", 5)
    }

    val OPERATION_ID = Regex("^[A-Za-z0-9_-]{8,64}$")
}
