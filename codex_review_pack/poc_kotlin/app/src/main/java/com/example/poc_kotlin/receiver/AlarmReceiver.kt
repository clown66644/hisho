package com.example.poc_kotlin.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.poc_kotlin.alarm.AlarmStore
import com.example.poc_kotlin.alarm.ExactAlarmScheduler

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(ExactAlarmScheduler.EXTRA_ALARM_ID) ?: return
        val title = intent.getStringExtra(ExactAlarmScheduler.EXTRA_TITLE) ?: "リマインダー"
        val message = intent.getStringExtra(ExactAlarmScheduler.EXTRA_MESSAGE) ?: "予定の時刻になりました。"

        showNotification(context, alarmId, title, message)
        AlarmStore(context).remove(alarmId)
    }

    private fun showNotification(context: Context, alarmId: String, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "butler_alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "執事・秘書アラーム通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "最重要予定・アラーム通知チャンネル"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alarmId.hashCode(), notification)
    }
}
