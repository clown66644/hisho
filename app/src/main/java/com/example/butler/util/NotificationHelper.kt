package com.example.butler.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.butler.receiver.NotificationActionReceiver
import com.example.butler.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_ID_ALARM = "butler_alarm_channel"
    const val CHANNEL_ID_SYNC = "butler_sync_channel"

    const val ACTION_COMPLETE = "com.example.butler.ACTION_COMPLETE"
    const val ACTION_SNOOZE = "com.example.butler.ACTION_SNOOZE"

    const val EXTRA_TODO_ID = "EXTRA_TODO_ID"
    const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
    const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "重要アラーム・リマインダー",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "予定時刻および緊急リマインダー通知"
                enableVibration(true)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC,
                "カレンダー同期・状況報告",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "バックグラウンドカレンダー同期や予定更新の通知"
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }

    fun buildReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        todoId: String? = null,
        alarmId: String? = null
    ): NotificationCompat.Builder {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)

        // アクション1: 「完了」ボタン
        if (todoId != null) {
            val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TODO_ID, todoId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 1,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.checkbox_on_background, "完了", completePendingIntent)
        }

        // アクション2: 「10分延期」ボタン
        if (alarmId != null) {
            val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_recent_history, "10分延期", snoozePendingIntent)
        }

        return builder
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        todoId: String? = null,
        alarmId: String? = null
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = buildReminderNotification(
            context,
            notificationId,
            title,
            message,
            todoId,
            alarmId
        )
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
