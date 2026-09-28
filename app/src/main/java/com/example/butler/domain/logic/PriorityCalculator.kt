package com.example.butler.domain.logic

import com.example.butler.domain.model.PriorityLevel
import com.example.butler.domain.model.TodoItem

class PriorityCalculator {

    /**
     * TodoItem の評価要素から内部数値スコアおよび PriorityLevel (最優先, 高, 中, 低) を算出する
     */
    fun calculatePriority(todo: TodoItem, currentTimeMillis: Long = System.currentTimeMillis()): Pair<Double, PriorityLevel> {
        // ユーザー固定優先度がある場合はそれを尊重
        if (todo.isPinnedPriority && todo.pinnedPriority != null) {
            val score = when (todo.pinnedPriority) {
                PriorityLevel.TOP_PRIORITY -> 100.0
                PriorityLevel.HIGH -> 75.0
                PriorityLevel.MEDIUM -> 50.0
                PriorityLevel.LOW -> 25.0
            }
            return Pair(score, todo.pinnedPriority)
        }

        var score = 0.0

        // 1. 最優先トリガー要素（健康・安全、取り返しのつかない損失）
        if (todo.isHealthOrSafety) {
            score += 50.0
        }
        if (todo.irretrievableLoss) {
            score += 40.0
        }

        // 2. お金・仕事への影響 (各 1..5)
        score += todo.financialImpact * 6.0
        score += todo.workImpact * 5.0

        // 3. 締切までの残り時間による緊急度評価
        todo.dueDate?.let { dueDate ->
            val remainingHours = (dueDate - currentTimeMillis) / (1000.0 * 60 * 60)
            when {
                remainingHours <= 0 -> score += 50.0 // 期限切れ/直前
                remainingHours <= 6 -> score += 35.0
                remainingHours <= 24 -> score += 20.0
                remainingHours <= 72 -> score += 10.0
            }
        }

        // 4. 精神的負荷の補正（負荷が高すぎる場合は着手障壁を調整）
        if (todo.mentalLoad >= 4) {
            score += 5.0
        }

        // 判定閾値
        val priorityLevel = when {
            score >= 60.0 || todo.isHealthOrSafety || todo.irretrievableLoss -> PriorityLevel.TOP_PRIORITY
            score >= 40.0 -> PriorityLevel.HIGH
            score >= 20.0 -> PriorityLevel.MEDIUM
            else -> PriorityLevel.LOW
        }

        return Pair(score, priorityLevel)
    }
}
