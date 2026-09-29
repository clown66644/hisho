package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.alarm.ScheduleResult
import com.example.butler.data.local.AppDatabase
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.security.DatabasePassphraseProvider
import com.example.butler.domain.model.TodoStatus
import com.example.butler.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class NotificationActionReceiver(
    private val todoDaoProvider: (() -> TodoDao?)? = null,
    private val historyDaoProvider: (() -> OperationHistoryDao?)? = null,
    private val alarmDaoProvider: (() -> AlarmDao?)? = null,
    private val schedulerProvider: ((Context) -> AlarmScheduler)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            NotificationHelper.ACTION_COMPLETE -> {
                val todoId = intent.getStringExtra(NotificationHelper.EXTRA_TODO_ID) ?: return
                handleCompleteTodo(context, todoId, notificationId)
            }
            NotificationHelper.ACTION_SNOOZE -> {
                val alarmId = intent.getStringExtra(NotificationHelper.EXTRA_ALARM_ID) ?: return
                handleSnoozeAlarm(context, alarmId, notificationId)
            }
        }
    }

    private fun handleCompleteTodo(context: Context, todoId: String, notificationId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var todoDao = todoDaoProvider?.invoke()
                var historyDao = historyDaoProvider?.invoke()
                if (todoDao == null || historyDao == null) {
                    val passphraseProvider = DatabasePassphraseProvider(context)
                    val passphrase = passphraseProvider.getOrGeneratePassphrase()
                    val db = AppDatabase.getInstance(context, passphrase)
                    if (todoDao == null) todoDao = db.todoDao()
                    if (historyDao == null) historyDao = db.operationHistoryDao()
                }
                val todo = todoDao.getTodoById(todoId)
                if (todo != null && todo.status != TodoStatus.COMPLETED.name) {
                    val previousJson = JSONObject().apply {
                        put("id", todo.id)
                        put("title", todo.title)
                        put("status", todo.status)
                        put("updatedAt", todo.updatedAt)
                    }.toString()

                    val updatedTodo = todo.copy(
                        status = TodoStatus.COMPLETED.name,
                        updatedAt = System.currentTimeMillis()
                    )
                    val newJson = JSONObject().apply {
                        put("id", updatedTodo.id)
                        put("title", updatedTodo.title)
                        put("status", updatedTodo.status)
                        put("updatedAt", updatedTodo.updatedAt)
                    }.toString()
                    val history = OperationHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        actor = "SYSTEM", // Fix M-004
                        userInput = null,
                        aiInterpretation = null,
                        actionType = "COMPLETE_TODO",
                        targetId = todoId,
                        previousStateJson = previousJson,
                        newStateJson = newJson,
                        status = "SUCCESS",
                        isUndone = false
                    )
                    val db = AppDatabase.getInstance(context, DatabasePassphraseProvider(context).getOrGeneratePassphrase())
                    db.runInTransaction {
                        todoDao.updateTodo(updatedTodo)
                        historyDao.insertHistory(history)
                    }
                    if (notificationId != -1) {
                        NotificationHelper.cancelNotification(context, notificationId)
                    }
                }
            } catch (e: Exception) {
                // 安全にフォールバック
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnoozeAlarm(context: Context, alarmId: String, notificationId: Int) {
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

                    val scheduler = schedulerProvider?.invoke(context) ?: AlarmScheduler(context)
                    val result = scheduler.scheduleExactAlarm(
                        alarmId = newAlarmId,
                        title = snoozedAlarm.title,
                        message = snoozedAlarm.message,
                        triggerAtMillis = snoozeTime
                    )

                    if (result is ScheduleResult.Scheduled) {
                        dao.insertAlarm(snoozedAlarm)
                        if (notificationId != -1) {
                            NotificationHelper.cancelNotification(context, notificationId)
                        }
                    }
                }
            } catch (e: Exception) {
                // 安全にフォールバック
            } finally {
                pendingResult.finish()
            }
        }
    }
}
