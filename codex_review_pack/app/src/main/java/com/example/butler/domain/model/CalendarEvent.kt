package com.example.butler.domain.model

import java.util.UUID

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val googleEventId: String? = null,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String? = null,
    val belongings: List<String> = emptyList(),
    val prepTasks: List<String> = emptyList(),
    val isAllDay: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
