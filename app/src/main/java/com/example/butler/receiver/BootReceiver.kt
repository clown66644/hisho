package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.worker.CalendarSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver(
    private val alarmDaoProvider: (() -> AlarmDao?)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            // バックグラウンドカレンダー同期を再登録
            try {
                CalendarSyncWorker.enqueuePeriodicSync(context)
            } catch (e: Exception) {
                // 安全にフォールバック
            }

            val scheduler = AlarmScheduler(context)
            if (!scheduler.canScheduleExactAlarms()) return

            val now = System.currentTimeMillis()
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarmDao = alarmDaoProvider?.invoke() ?: run {
                        try {
                            AppDatabase.getInstance(context).alarmDao()
                        } catch (e: Exception) {
                            null
                        }
                    } ?: return@launch

                    val pendingAlarms = alarmDao.getPendingFutureAlarms(now)

                    pendingAlarms.forEach { alarm ->
                        // 発火済み・期限切れ・キャンセル済みは再セットしない
                        if (!alarm.isFired && !alarm.isCancelled && alarm.triggerAtMillis > now) {
                            scheduler.scheduleExactAlarm(
                                alarmId = alarm.id,
                                title = alarm.title,
                                message = alarm.message,
                                triggerAtMillis = alarm.triggerAtMillis
                            )
                        }
                    }
                } catch (e: Exception) {
                    // 安全にエラー復帰
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }
}
