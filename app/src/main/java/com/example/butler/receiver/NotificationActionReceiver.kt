package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.data.local.security.DatabasePassphraseProvider
import com.example.butler.domain.model.TodoStatus
import com.example.butler.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationActionReceiver(
    private val todoDaoProvider: (() -> TodoDao?)? = null,
    private val alarmDaoProvider: (() -> AlarmDao?)? = null,
    private val schedulerProvider: ((Context) -> AlarmScheduler)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(context, notificationId)
        }

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE -> {
                val todoId = intent.getStringExtra(NotificationHelper.EXTRA_TODO_ID) ?: return
                handleCompleteTodo(context, todoId)
            }
            NotificationHelper.ACTION_SNOOZE -> {
                val alarmId = intent.getStringExtra(NotificationHelper.EXTRA_ALARM_ID) ?: return
                handleSnoozeAlarm(context, alarmId)
            }
        }
    }

    private fun handleCompleteTodo(context: Context, todoId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = todoDaoProvider?.invoke() ?: run {
                    val passphraseProvider = DatabasePassphraseProvider(context)
                    val passphrase = passphraseProvider.getOrGeneratePassphrase()
                    val db = AppDatabase.getInstance(context, passphrase)
                    db.todoDao()
                }
                val todo = dao.getTodoById(todoId)
                if (todo != null) {
                    dao.updateTodo(todo.copy(
                        status = TodoStatus.COMPLETED.name,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            } catch (e: Exception) {
                // 安全にフォールバック
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnoozeAlarm(context: Context, alarmId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = alarmDaoProvider?.invoke() ?: run {
                    val passphraseProvider = DatabasePassphraseProvider(context)
                    val passphrase = passphraseProvider.getOrGeneratePassphrase()
                    val db = AppDatabase.getInstance(context, passphrase)
                    db.alarmDao()
                }
                val alarm = dao.getAlarmById(alarmId)
                if (alarm != null) {
                    val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L // 10分後
                    val newAlarmId = UUID.randomUUID().toString()
                    val snoozedAlarm = AlarmItemEntity(
                        id = newAlarmId,
                        title = "${alarm.title} (延期)",
                        message = alarm.message,
                        triggerAtMillis = snoozeTime,
                        isFired = false,
                        isCancelled = false
                    )
                    dao.insertAlarm(snoozedAlarm)

                    val scheduler = schedulerProvider?.invoke(context) ?: AlarmScheduler(context)
                    scheduler.scheduleExactAlarm(
                        alarmId = newAlarmId,
                        title = snoozedAlarm.title,
                        message = snoozedAlarm.message,
                        triggerAtMillis = snoozeTime
                    )
                }
            } catch (e: Exception) {
                // 安全にフォールバック
            } finally {
                pendingResult.finish()
            }
        }
    }
}
