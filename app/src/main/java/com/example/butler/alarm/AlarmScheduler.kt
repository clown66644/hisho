package com.example.butler.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.butler.receiver.AlarmReceiver

sealed class ScheduleResult {
    object Scheduled : ScheduleResult()
    object PermissionRequired : ScheduleResult()
    data class Failed(val reason: String) : ScheduleResult()
}

open class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    open fun canScheduleExactAlarms(): Boolean {
        if (alarmManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    open fun scheduleExactAlarm(
        alarmId: String,
        title: String,
        message: String,
        triggerAtMillis: Long
    ): ScheduleResult {
        if (alarmManager == null) {
            return ScheduleResult.Failed("AlarmManager を取得できません。")
        }

        if (!canScheduleExactAlarms()) {
            return ScheduleResult.PermissionRequired
        }

        return try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                data = Uri.parse("content://com.example.butler/alarm/$alarmId")
                putExtra("ALARM_ID", alarmId)
                putExtra("TITLE", title)
                putExtra("MESSAGE", message)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            ScheduleResult.Scheduled
        } catch (e: Exception) {
            ScheduleResult.Failed("アラーム設定中に例外が発生しました。")
        }
    }

    open fun cancelAlarm(alarmId: String) {
        if (alarmManager == null) return
        try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                data = Uri.parse("content://com.example.butler/alarm/$alarmId")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // キャンセル失敗時も安全に復帰
        }
    }
}
