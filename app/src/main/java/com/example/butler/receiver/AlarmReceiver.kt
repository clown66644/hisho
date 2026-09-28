package com.example.butler.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.security.DatabasePassphraseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return
        val title = intent.getStringExtra("TITLE") ?: "リマインダー"
        val message = intent.getStringExtra("MESSAGE") ?: "予定の時刻になりました。"

        showNotification(context, alarmId, title, message)
        markAlarmAsFired(context, alarmId)
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alarmId.hashCode(), notification)
    }

    private fun markAlarmAsFired(context: Context, alarmId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val passphraseProvider = DatabasePassphraseProvider(context)
                val passphrase = passphraseProvider.getOrGeneratePassphrase()
                val db = AppDatabase.getInstance(context, passphrase)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    db.alarmDao().updateAlarm(alarm.copy(isFired = true))
                }
            } catch (e: Exception) {
                // DB更新例外時も安全に非同期処理を終了
            } finally {
                pendingResult.finish()
            }
        }
    }
}
