package com.example.butler.domain.model

import java.util.UUID

enum class TodoStatus {
    UNSTARTED,   // 未着手
    IN_PROGRESS, // 進行中
    PAUSED,      // 一時停止
    COMPLETED,   // 完了
    POSTPONED,   // 延期
    CANCELLED,   // 中止
    ARCHIVED     // アーカイブ
}

enum class PriorityLevel {
    TOP_PRIORITY, // 最優先
    HIGH,         // 高
    MEDIUM,       // 中
    LOW           // 低
}

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val detail: String? = null,
    val memo: String? = null,
    val dueDate: Long? = null,
    val scheduledStartTime: Long? = null,
    val scheduledEndTime: Long? = null,
    val estimatedMinutes: Int? = null,
    val status: TodoStatus = TodoStatus.UNSTARTED,
    
    // 評価要素
    val isHealthOrSafety: Boolean = false, // 健康・安全に関わるか
    val financialImpact: Int = 1,          // お金への影響 (1-5)
    val workImpact: Int = 1,               // 仕事への影響 (1-5)
    val irretrievableLoss: Boolean = false, // 放置時の取り返しのつかない損失
    val mentalLoad: Int = 1,               // 精神的負荷 (1-5)
    val requiredStamina: Int = 1,          // 必要体力 (1-5)
    val isPinnedPriority: Boolean = false, // ユーザーによる優先度固定フラグ
    val pinnedPriority: PriorityLevel? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
