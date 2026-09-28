package com.example.butler.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val isFired: Boolean = false,
    val isCancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
