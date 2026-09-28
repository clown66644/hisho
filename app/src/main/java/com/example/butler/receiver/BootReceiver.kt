package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.data.local.dao.AlarmDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver(
    private val alarmDaoProvider: (() -> AlarmDao?)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val scheduler = AlarmScheduler(context)
            if (!scheduler.canScheduleExactAlarms()) return

            val now = System.currentTimeMillis()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarmDao = alarmDaoProvider?.invoke() ?: return@launch
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
                }
            }
        }
    }
}
