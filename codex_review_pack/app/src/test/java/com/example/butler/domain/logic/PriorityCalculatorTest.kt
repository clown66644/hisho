package com.example.butler.domain.logic

import com.example.butler.domain.model.PriorityLevel
import com.example.butler.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityCalculatorTest {

    private val calculator = PriorityCalculator()

    @Test
    fun testHealthAndSafetyTriggersTopPriority() {
        val todo = TodoItem(
            title = "服薬",
            isHealthOrSafety = true
        )
        val (_, priorityLevel) = calculator.calculatePriority(todo)
        assertEquals(PriorityLevel.TOP_PRIORITY, priorityLevel)
    }

    @Test
    fun testPinnedPriorityOverridesCalculation() {
        val todo = TodoItem(
            title = "散歩",
            isPinnedPriority = true,
            pinnedPriority = PriorityLevel.LOW
        )
        val (score, priorityLevel) = calculator.calculatePriority(todo)
        assertEquals(25.0, score, 0.01)
        assertEquals(PriorityLevel.LOW, priorityLevel)
    }

    @Test
    fun pinnedLowCannotOverrideHealthAndSafety() {
        val todo = TodoItem(
            title = "緊急の服薬確認",
            isHealthOrSafety = true,
            isPinnedPriority = true,
            pinnedPriority = PriorityLevel.LOW,
        )

        val (score, priorityLevel) = calculator.calculatePriority(todo)

        assertEquals(100.0, score, 0.01)
        assertEquals(PriorityLevel.TOP_PRIORITY, priorityLevel)
    }

    @Test
    fun pinnedLowCannotOverrideIrretrievableLoss() {
        val todo = TodoItem(
            title = "取消不能期限",
            irretrievableLoss = true,
            isPinnedPriority = true,
            pinnedPriority = PriorityLevel.LOW,
        )

        val (_, priorityLevel) = calculator.calculatePriority(todo)

        assertEquals(PriorityLevel.TOP_PRIORITY, priorityLevel)
    }

    @Test
    fun outOfRangeFactorsAreClamped() {
        val todo = TodoItem(
            title = "旧版データ",
            financialImpact = 100,
            workImpact = -10,
        )

        val (score, _) = calculator.calculatePriority(todo)

        assertEquals(35.0, score, 0.01)
    }

    @Test
    fun testFinancialAndWorkImpactCalculation() {
        val todo = TodoItem(
            title = "税金支払い",
            financialImpact = 5, // 5 * 6 = 30
            workImpact = 4       // 4 * 5 = 20 -> sum 50 >= 40 (HIGH)
        )
        val (score, priorityLevel) = calculator.calculatePriority(todo)
        assertEquals(50.0, score, 0.01)
        assertEquals(PriorityLevel.HIGH, priorityLevel)
    }
}
