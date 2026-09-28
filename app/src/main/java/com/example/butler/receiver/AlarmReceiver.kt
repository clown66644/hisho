package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.security.DatabasePassphraseProvider
import com.example.butler.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return
        val title = intent.getStringExtra("TITLE") ?: "リマインダー"
        val message = intent.getStringExtra("MESSAGE") ?: "予定の時刻になりました。"
        val todoId = intent.getStringExtra("TODO_ID")

        NotificationHelper.showNotification(
            context = context,
            notificationId = alarmId.hashCode(),
            title = title,
            message = message,
            todoId = todoId,
            alarmId = alarmId
        )
        markAlarmAsFired(context, alarmId)
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
