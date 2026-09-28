package com.example.butler.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.butler.data.local.CalendarSyncManager
import com.example.butler.util.NotificationHelper
import java.util.concurrent.TimeUnit

class CalendarSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val syncManager = CalendarSyncManager(context)
            if (!syncManager.hasReadPermission()) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val next24Hours = now + 24 * 60 * 60 * 1000L
            val upcomingEvents = syncManager.getEvents(now, next24Hours)

            // 重複予定の検知
            for (event in upcomingEvents) {
                val duplicates = syncManager.detectDuplicates(event)
                if (duplicates.isNotEmpty()) {
                    NotificationHelper.showNotification(
                        context = context,
                        notificationId = event.id.hashCode(),
                        title = "予定の重複を検知しました",
                        message = "「${event.title}」と時間帯が重複する予定が存在します。"
                    )
                    break
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ButlerCalendarSyncWorker"

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
