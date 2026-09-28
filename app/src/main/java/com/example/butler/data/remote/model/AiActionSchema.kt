package com.example.butler.data.remote.model

enum class AiActionType {
    CREATE_TODO,               // ToDo追加候補作成 (自動実行可)
    UPDATE_TODO,               // ToDo変更 (確認必須)
    DELETE_TODO,               // ToDo削除 (確認必須)
    UPDATE_EVENT,              // 予定変更 (確認必須)
    DELETE_EVENT,              // 予定削除 (確認必須)
    COMPLETE_MULTIPLE_TODOS,   // 一括操作 (確認必須)
    UPDATE_SETTINGS,           // 重要設定変更 (確認必須)
    SEND_MESSAGE,              // 外部メッセージ送信 (初期版禁止)
    EXECUTE_PAYMENT,           // 支払い・購入・契約 (初期版禁止)
    CHANGE_MEDICATION          // 服薬変更 (初期版禁止)
}

data class AiTodoPayload(
    val title: String,
    val detail: String? = null,
    val dueDate: Long? = null,
    val estimatedMinutes: Int? = null,
    val isHealthOrSafety: Boolean = false,
    val financialImpact: Int = 1,
    val workImpact: Int = 1,
    val mentalLoad: Int = 1
)

data class AiActionRequest(
    val operationId: String,
    val actionType: String,
    val payloadJson: String,
    val rationale: String? = null
)
