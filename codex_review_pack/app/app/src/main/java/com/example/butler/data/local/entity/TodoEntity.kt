package com.example.butler.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.butler.domain.model.PriorityLevel
import com.example.butler.domain.model.TodoItem
import com.example.butler.domain.model.TodoStatus

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val detail: String?,
    val memo: String?,
    val dueDate: Long?,
    val scheduledStartTime: Long?,
    val scheduledEndTime: Long?,
    val estimatedMinutes: Int?,
    val status: String,
    val isHealthOrSafety: Boolean,
    val financialImpact: Int,
    val workImpact: Int,
    val irretrievableLoss: Boolean,
    val mentalLoad: Int,
    val requiredStamina: Int,
    val isPinnedPriority: Boolean,
    val pinnedPriority: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomainModel(): TodoItem {
        return TodoItem(
            id = id,
            title = title,
            detail = detail,
            memo = memo,
            dueDate = dueDate,
            scheduledStartTime = scheduledStartTime,
            scheduledEndTime = scheduledEndTime,
            estimatedMinutes = estimatedMinutes,
            status = TodoStatus.valueOf(status),
            isHealthOrSafety = isHealthOrSafety,
            financialImpact = financialImpact,
            workImpact = workImpact,
            irretrievableLoss = irretrievableLoss,
            mentalLoad = mentalLoad,
            requiredStamina = requiredStamina,
            isPinnedPriority = isPinnedPriority,
            pinnedPriority = pinnedPriority?.let { PriorityLevel.valueOf(it) },
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomainModel(todo: TodoItem): TodoEntity {
            return TodoEntity(
                id = todo.id,
                title = todo.title,
                detail = todo.detail,
                memo = todo.memo,
                dueDate = todo.dueDate,
                scheduledStartTime = todo.scheduledStartTime,
                scheduledEndTime = todo.scheduledEndTime,
                estimatedMinutes = todo.estimatedMinutes,
                status = todo.status.name,
                isHealthOrSafety = todo.isHealthOrSafety,
                financialImpact = todo.financialImpact,
                workImpact = todo.workImpact,
                irretrievableLoss = todo.irretrievableLoss,
                mentalLoad = todo.mentalLoad,
                requiredStamina = todo.requiredStamina,
                isPinnedPriority = todo.isPinnedPriority,
                pinnedPriority = todo.pinnedPriority?.name,
                createdAt = todo.createdAt,
                updatedAt = todo.updatedAt
            )
        }
    }
}
