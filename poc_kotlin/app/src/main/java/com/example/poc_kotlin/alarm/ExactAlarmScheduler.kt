package com.example.poc_kotlin.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.poc_kotlin.receiver.AlarmReceiver

sealed interface AlarmScheduleResult {
    data class Scheduled(val alarmId: String) : AlarmScheduleResult
    data object ExactAlarmPermissionRequired : AlarmScheduleResult
    data class Failed(val reason: String) : AlarmScheduleResult
}

class ExactAlarmScheduler(private val context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val alarmStore = AlarmStore(appContext)

    fun scheduleExactAlarm(alarm: StoredAlarm): AlarmScheduleResult {
        if (alarm.id.isBlank() || alarm.triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleResult.Failed("アラームIDまたは発火時刻が不正です")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return AlarmScheduleResult.ExactAlarmPermissionRequired
        }

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAtMillis,
                pendingIntentFor(alarm),
            )
            // OSへの予約が受理された後だけ復旧対象として保存する。
            try {
                alarmStore.upsert(alarm)
                AlarmScheduleResult.Scheduled(alarm.id)
            } catch (storageError: Exception) {
                // 予約だけが残る部分成功を避ける。再試行は同じalarmIdを使うこと。
                alarmManager.cancel(pendingIntentFor(alarm))
                AlarmScheduleResult.Failed(
                    storageError.message ?: "予約後の永続化に失敗したため予約を取り消しました"
                )
            }
        } catch (error: Exception) {
            AlarmScheduleResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun cancelAlarm(alarmId: String): Boolean = runCatching {
        alarmManager.cancel(pendingIntentForId(alarmId))
        alarmStore.remove(alarmId)
        true
    }.getOrDefault(false)

    private fun pendingIntentFor(alarm: StoredAlarm): PendingIntent {
        val intent = baseIntent(alarm.id).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_TITLE, alarm.title)
            putExtra(EXTRA_MESSAGE, alarm.message)
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingIntentForId(alarmId: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        baseIntent(alarmId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) ?: PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        baseIntent(alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun baseIntent(alarmId: String) = Intent(appContext, AlarmReceiver::class.java).apply {
        // data URIをPendingIntentの同一性に含め、同名アラームの上書きを防ぐ。
        data = Uri.parse("butler-poc://alarm/${Uri.encode(alarmId)}")
    }

    companion object {
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_TITLE = "TITLE"
        const val EXTRA_MESSAGE = "MESSAGE"
        private const val REQUEST_CODE = 0
    }
}
