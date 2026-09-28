package com.example.butler.domain.logic

enum class PolicyLevel {
    AUTO_EXECUTABLE,       // 自動実行可能 (ToDo追加候補作成など)
    CONFIRMATION_REQUIRED, // 確認必須 (予定/ToDoの変更・削除・一括操作・重要設定変更など)
    FORBIDDEN              // 初期版絶対禁止 (支払い・購入・契約・服薬変更・外部送信など)
}

class ForbiddenOperationException(message: String = "この操作は初期版セキュリティポリシーにより禁止されています。") : Exception(message)

object OperationPolicyManager {

    private val forbiddenActions = setOf(
        "SEND_MESSAGE",
        "EXECUTE_PAYMENT",
        "PAYMENT",
        "PURCHASE",
        "CONTRACT",
        "CHANGE_MEDICATION",
        "STOP_MEDICATION",
        "MEDICAL_DIAGNOSIS",
        "EXTERNAL_CONTACT"
    )

    private val confirmationRequiredActions = setOf(
        "UPDATE_TODO",
        "DELETE_TODO",
        "UPDATE_EVENT",
        "DELETE_EVENT",
        "COMPLETE_MULTIPLE_TODOS",
        "BULK_OPERATION",
        "UPDATE_MEDICATION_RECORD",
        "UPDATE_SETTINGS"
    )

    private val autoExecutableActions = setOf(
        "CREATE_TODO",
        "CREATE_EVENT",
        "SEARCH_INFO",
        "VIEW_SCHEDULE"
    )

    fun evaluatePolicy(actionType: String): PolicyLevel {
        val upperAction = actionType.uppercase()
        return when {
            forbiddenActions.contains(upperAction) -> PolicyLevel.FORBIDDEN
            confirmationRequiredActions.contains(upperAction) -> PolicyLevel.CONFIRMATION_REQUIRED
            autoExecutableActions.contains(upperAction) -> PolicyLevel.AUTO_EXECUTABLE
            else -> PolicyLevel.CONFIRMATION_REQUIRED // 未知の操作は安全側に倒して確認必須
        }
    }

    fun assertOperationAllowed(actionType: String) {
        val policy = evaluatePolicy(actionType)
        if (policy == PolicyLevel.FORBIDDEN) {
            throw ForbiddenOperationException("禁止された外部操作/医療/メッセージ操作 ($actionType) の実行が拒否されました。")
        }
    }
}
