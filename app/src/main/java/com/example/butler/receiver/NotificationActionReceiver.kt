package com.example.butler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
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
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                var todoDao = todoDaoProvider?.invoke()
                var historyDao = historyDaoProvider?.invoke()
                if (todoDao == null || historyDao == null) {
                    val passphraseProvider = com.example.butler.data.local.security.DatabasePassphraseProvider(context)
                    val passphrase = passphraseProvider.getOrGeneratePassphrase()
                    val db = com.example.butler.data.local.AppDatabase.getInstance(context, passphrase)
                    if (todoDao == null) todoDao = db.todoDao()
                    if (historyDao == null) historyDao = db.operationHistoryDao()
                }
                val todo = todoDao.getTodoById(todoId)
                if (todo != null && todo.status != com.example.butler.domain.model.TodoStatus.COMPLETED.name) {
                    val oldDomain = todo.toDomainModel()
                    val newDomain = oldDomain.copy(
                        status = com.example.butler.domain.model.TodoStatus.COMPLETED,
                        updatedAt = System.currentTimeMillis()
                    )

                    val previousJson = com.example.butler.domain.logic.CommandResolver.todoToJson(oldDomain)
                    val newJson = com.example.butler.domain.logic.CommandResolver.todoToJson(newDomain)

                    val history = com.example.butler.domain.model.OperationHistory(
                        id = java.util.UUID.randomUUID().toString(),
                        actor = com.example.butler.domain.model.Actor.SYSTEM,
                        actionType = "COMPLETE_TODO",
                        targetId = todoId,
                        previousStateJson = previousJson,
                        newStateJson = newJson
                    )
                    
                    val command = com.example.butler.domain.logic.UpdateTodoCommand(
                        history = history,
                        newTodo = newDomain,
                        oldTodo = oldDomain,
                        todoDao = todoDao,
                        inMemoryTodoList = null
                    )
                    val undoManager = com.example.butler.domain.logic.UndoManager(historyDao)
                    val success = undoManager.executeCommand(command)

                    if (success && notificationId != -1) {
                        com.example.butler.util.NotificationHelper.cancelNotification(context, notificationId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationAction", "Failed to complete todo. todoId=" + todoId, e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "操作を完了できませんでした", android.widget.Toast.LENGTH_SHORT).show()
                }
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
                        try {
                            dao.insertAlarm(snoozedAlarm)
                            if (notificationId != -1) {
                                NotificationHelper.cancelNotification(context, notificationId)
                            }
                        } catch (e: Exception) {
                            scheduler.cancelAlarm(newAlarmId)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationAction", "Failed to snooze alarm. alarmId=" + alarmId, e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "操作を完了できませんでした", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
