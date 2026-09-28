package com.example.butler.domain.model

import java.util.UUID

enum class Actor {
    USER,
    AI_BUTLER,
    AI_MAID,
    SYSTEM
}

enum class OperationStatus {
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS
}

data class OperationHistory(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val actor: Actor,
    val userInput: String? = null,
    val aiInterpretation: String? = null,
    val actionType: String, // 例: "CREATE_TODO", "UPDATE_TODO", "DELETE_EVENT"
    val targetId: String,   // 操作対象のエンティティID
    val previousStateJson: String? = null, // 変更前状態 (暗号化/JSON)
    val newStateJson: String? = null,      // 変更後状態 (暗号化/JSON)
    val status: OperationStatus = OperationStatus.SUCCESS,
    val isUndone: Boolean = false
)
