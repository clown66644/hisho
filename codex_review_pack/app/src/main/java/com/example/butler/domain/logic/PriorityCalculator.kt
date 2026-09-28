package com.example.butler.domain.logic

import com.example.butler.domain.model.PriorityLevel
import com.example.butler.domain.model.TodoItem

class PriorityCalculator {

    /**
     * TodoItem の評価要素から内部数値スコアおよび PriorityLevel (最優先, 高, 中, 低) を算出する
     */
    fun calculatePriority(todo: TodoItem, currentTimeMillis: Long = System.currentTimeMillis()): Pair<Double, PriorityLevel> {
        // 安全性と不可逆損失はユーザー固定値より優先する。
        // 誤ってLOWへ固定された場合でも、安全に関わるタスクを埋没させない。
        if (todo.isHealthOrSafety || todo.irretrievableLoss) {
            return Pair(MAX_SCORE, PriorityLevel.TOP_PRIORITY)
        }

        // 安全上の強制昇格対象でない場合だけ、ユーザー固定優先度を尊重する。
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
        // 1. お金・仕事への影響。破損・旧版データの範囲外値は安全に丸める。
        score += todo.financialImpact.coerceIn(MIN_FACTOR, MAX_FACTOR) * 6.0
        score += todo.workImpact.coerceIn(MIN_FACTOR, MAX_FACTOR) * 5.0

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
        score = score.coerceIn(0.0, MAX_SCORE)
        val priorityLevel = when {
            score >= 60.0 || todo.isHealthOrSafety || todo.irretrievableLoss -> PriorityLevel.TOP_PRIORITY
            score >= 40.0 -> PriorityLevel.HIGH
            score >= 20.0 -> PriorityLevel.MEDIUM
            else -> PriorityLevel.LOW
        }

        return Pair(score, priorityLevel)
    }

    private companion object {
        const val MIN_FACTOR = 1
        const val MAX_FACTOR = 5
        const val MAX_SCORE = 100.0
    }
}
