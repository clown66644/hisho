package com.example.butler.domain.logic

import com.example.butler.data.remote.model.AiActionType

enum class PolicyLevel {
    AUTO_EXECUTABLE,
    CONFIRMATION_REQUIRED,
    FORBIDDEN,
}

class ForbiddenOperationException(actionType: String) :
    IllegalArgumentException("禁止された操作です: $actionType")

class ConfirmationRequiredException(actionType: String) :
    IllegalArgumentException("本人確認が必要な操作です: $actionType")

object OperationPolicyManager {
    private val forbiddenActions = setOf(
        AiActionType.SEND_MESSAGE,
        AiActionType.EXECUTE_PAYMENT,
        AiActionType.CHANGE_MEDICATION,
    )

    fun evaluatePolicy(actionType: AiActionType): PolicyLevel = when {
        actionType in forbiddenActions -> PolicyLevel.FORBIDDEN
        actionType == AiActionType.CREATE_TODO -> PolicyLevel.AUTO_EXECUTABLE
        else -> PolicyLevel.CONFIRMATION_REQUIRED
    }

    /**
     * 未確認操作を「許可済み」と誤解させないため、自動実行可能な操作だけを通す。
     * 確認必須操作は将来の確認UIが確認済み証跡を提供するまでCommand化しない。
     */
    fun requireAutoExecutable(actionType: AiActionType) {
        when (evaluatePolicy(actionType)) {
            PolicyLevel.AUTO_EXECUTABLE -> Unit
            PolicyLevel.CONFIRMATION_REQUIRED -> throw ConfirmationRequiredException(actionType.name)
            PolicyLevel.FORBIDDEN -> throw ForbiddenOperationException(actionType.name)
        }
    }
}
