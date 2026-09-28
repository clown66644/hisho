package com.example.butler.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import com.example.butler.domain.model.OperationPhase
import com.example.butler.domain.model.OperationStatus

@Entity(tableName = "operation_histories")
data class OperationHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val actor: String,
    val userInput: String?,
    val aiInterpretation: String?,
    val actionType: String,
    val targetId: String,
    val previousStateJson: String?,
    val newStateJson: String?,
    val status: String,
    val isUndone: Boolean,
    val isRedoable: Boolean,
    val lastPhase: String,
    val lastResult: String,
    val attemptCount: Int,
    val updatedAt: Long,
    val compensationResult: String?,
    @ColumnInfo(defaultValue = "0")
    val operationOrder: Long,
) {
    fun toDomainModel(): OperationHistory = OperationHistory(
        id = id,
        timestamp = timestamp,
        actor = actor.toEnumOrDefault(Actor.SYSTEM),
        userInput = userInput,
        aiInterpretation = aiInterpretation,
        actionType = actionType,
        targetId = targetId,
        previousStateJson = previousStateJson,
        newStateJson = newStateJson,
        status = status.toEnumOrDefault(OperationStatus.FAILED),
        isUndone = isUndone,
        isRedoable = isRedoable,
        lastPhase = lastPhase.toEnumOrDefault(OperationPhase.EXECUTE),
        lastResult = lastResult.toEnumOrDefault(OperationStatus.FAILED),
        attemptCount = attemptCount,
        updatedAt = updatedAt,
        compensationResult = compensationResult?.toEnumOrDefault(OperationStatus.FAILED),
        operationOrder = operationOrder,
    )

    companion object {
        fun fromDomainModel(history: OperationHistory): OperationHistoryEntity =
            OperationHistoryEntity(
                id = history.id,
                timestamp = history.timestamp,
                actor = history.actor.name,
                userInput = history.userInput,
                aiInterpretation = history.aiInterpretation,
                actionType = history.actionType,
                targetId = history.targetId,
                previousStateJson = history.previousStateJson,
                newStateJson = history.newStateJson,
                status = history.status.name,
                isUndone = history.isUndone,
                isRedoable = history.isRedoable,
                lastPhase = history.lastPhase.name,
                lastResult = history.lastResult.name,
                attemptCount = history.attemptCount,
                updatedAt = history.updatedAt,
                compensationResult = history.compensationResult?.name,
                operationOrder = history.operationOrder,
            )
    }
}

private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
