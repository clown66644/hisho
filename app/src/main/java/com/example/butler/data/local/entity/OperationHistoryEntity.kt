package com.example.butler.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
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
    val isUndone: Boolean
) {
    fun toDomainModel(): OperationHistory {
        return OperationHistory(
            id = id,
            timestamp = timestamp,
            actor = try { Actor.valueOf(actor) } catch (e: Exception) { Actor.SYSTEM },
            userInput = userInput,
            aiInterpretation = aiInterpretation,
            actionType = actionType,
            targetId = targetId,
            previousStateJson = previousStateJson,
            newStateJson = newStateJson,
            status = try { OperationStatus.valueOf(status) } catch (e: Exception) { OperationStatus.FAILED },
            isUndone = isUndone
        )
    }

    companion object {
        fun fromDomainModel(history: OperationHistory): OperationHistoryEntity {
            return OperationHistoryEntity(
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
                isUndone = history.isUndone
            )
        }
    }
}
