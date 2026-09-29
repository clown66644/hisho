package com.example.butler.worker

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.alarm.ScheduleResult
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.domain.model.TodoStatus
import com.example.butler.receiver.NotificationActionReceiver
import com.example.butler.util.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class NotificationAndSyncTest {

    private class FakeTodoDao : TodoDao {
        val db = mutableMapOf<String, TodoEntity>()

        override suspend fun getAllTodos(): List<TodoEntity> = db.values.toList()
        override suspend fun getTodoById(id: String): TodoEntity? = db[id]
        override suspend fun insertTodo(todo: TodoEntity) { db[todo.id] = todo }
        override suspend fun updateTodo(todo: TodoEntity) { db[todo.id] = todo }
        override suspend fun deleteTodo(todo: TodoEntity) { db.remove(todo.id) }
    }

    private class FakeAlarmDao : AlarmDao {
        val db = mutableMapOf<String, AlarmItemEntity>()

        override suspend fun getAllAlarms(): List<AlarmItemEntity> = db.values.toList()
        override suspend fun getAlarmById(id: String): AlarmItemEntity? = db[id]
        override suspend fun getPendingFutureAlarms(currentTime: Long): List<AlarmItemEntity> =
            db.values.filter { !it.isFired && !it.isCancelled && it.triggerAtMillis > currentTime }
        override suspend fun insertAlarm(alarm: AlarmItemEntity) { db[alarm.id] = alarm }
        override suspend fun updateAlarm(alarm: AlarmItemEntity) { db[alarm.id] = alarm }
        override suspend fun deleteAlarm(alarm: AlarmItemEntity) { db.remove(alarm.id) }
    }

    private class FakeOperationHistoryDao : com.example.butler.data.local.dao.OperationHistoryDao {
        val db = mutableMapOf<String, com.example.butler.data.local.entity.OperationHistoryEntity>()

        override suspend fun getAllHistories(): List<com.example.butler.data.local.entity.OperationHistoryEntity> = db.values.toList()
        override suspend fun getHistoryById(id: String): com.example.butler.data.local.entity.OperationHistoryEntity? = db[id]
        override suspend fun getUndoableHistories(): List<com.example.butler.data.local.entity.OperationHistoryEntity> =
            db.values.filter { !it.isUndone && it.status == "SUCCESS" }
        override suspend fun insertHistory(history: com.example.butler.data.local.entity.OperationHistoryEntity): Long {
            db[history.id] = history
            return 1L
        }
        override suspend fun updateHistory(history: com.example.butler.data.local.entity.OperationHistoryEntity) {
            db[history.id] = history
        }
        override suspend fun deleteHistoryById(id: String) {
            db.remove(id)
        }
    }

    private class FakeAlarmScheduler(context: Context) : AlarmScheduler(context) {
        val scheduledAlarms = mutableMapOf<String, Long>()
        var shouldFail: Boolean = false

        override fun canScheduleExactAlarms(): Boolean = !shouldFail
        override fun scheduleExactAlarm(
            alarmId: String,
            title: String,
            message: String,
            triggerAtMillis: Long
        ): ScheduleResult {
            if (shouldFail) {
                return ScheduleResult.PermissionRequired
            }
            scheduledAlarms[alarmId] = triggerAtMillis
            return ScheduleResult.Scheduled
        }
        override fun cancelAlarm(alarmId: String) {
            scheduledAlarms.remove(alarmId)
        }
    }

    private lateinit var context: Context
    private lateinit var fakeTodoDao: FakeTodoDao
    private lateinit var fakeAlarmDao: FakeAlarmDao
    private lateinit var fakeHistoryDao: FakeOperationHistoryDao
    private lateinit var fakeScheduler: FakeAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeTodoDao = FakeTodoDao()
        fakeAlarmDao = FakeAlarmDao()
        fakeHistoryDao = FakeOperationHistoryDao()
        fakeScheduler = FakeAlarmScheduler(context)
    }

    @Test
    fun testNotificationChannelsCreation() {
        NotificationHelper.createNotificationChannels(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM)
        assertNotNull(alarmChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alarmChannel.importance)

        val syncChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_SYNC)
        assertNotNull(syncChannel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, syncChannel.importance)
    }

    @Test
    fun testBuildReminderNotificationWithActions() {
        val builder = NotificationHelper.buildReminderNotification(
            context = context,
            notificationId = 101,
            title = "薬を飲む",
            message = "食後の服薬時刻です。",
            todoId = "todo-med-1",
            alarmId = "alarm-med-1"
        )

        val notification = builder.build()
        assertEquals("薬を飲む", notification.extras.getString("android.title"))
        assertEquals("食後の服薬時刻です。", notification.extras.getString("android.text"))

        // アクションボタンが2つ登録されていること（完了、10分延期）
        assertEquals(2, notification.actions.size)
        assertEquals("完了", notification.actions[0].title)
        assertEquals("10分延期", notification.actions[1].title)
    }

    @Test
    fun testNotificationActionReceiverCompleteAction() = runBlocking {
        // テスト用 ToDo の挿入
        val todo = TodoEntity(
            id = "test-todo-action-1",
            title = "報告書作成",
            detail = null,
            memo = null,
            dueDate = null,
            scheduledStartTime = null,
            scheduledEndTime = null,
            estimatedMinutes = null,
            status = TodoStatus.UNSTARTED.name,
            isHealthOrSafety = false,
            financialImpact = 1,
            workImpact = 1,
            irretrievableLoss = false,
            mentalLoad = 1,
            requiredStamina = 1,
            isPinnedPriority = false,
            pinnedPriority = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        fakeTodoDao.insertTodo(todo)

        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationHelper.ACTION_COMPLETE
            putExtra(NotificationHelper.EXTRA_TODO_ID, "test-todo-action-1")
            putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 201)
        }

        val receiver = NotificationActionReceiver(
            todoDaoProvider = { fakeTodoDao },
            historyDaoProvider = { fakeHistoryDao },
            alarmDaoProvider = { fakeAlarmDao },
            schedulerProvider = { fakeScheduler }
        )
        receiver.onReceive(context, intent)

        // 非同期更新を待つ
        kotlinx.coroutines.delay(200)

        val updated = fakeTodoDao.getTodoById("test-todo-action-1")
        assertNotNull(updated)
        assertEquals(TodoStatus.COMPLETED.name, updated!!.status)

        // 操作履歴（OperationHistoryEntity）が記録されていること
        val histories = fakeHistoryDao.getAllHistories()
        assertEquals(1, histories.size)
        val history = histories[0]
        assertEquals("COMPLETE_TODO", history.actionType)
        assertEquals("NOTIFICATION", history.actor)
        assertEquals("test-todo-action-1", history.targetId)
        assertNotNull(history.previousStateJson)
        assertNotNull(history.newStateJson)
    }

    @Test
    fun testNotificationActionReceiverSnoozeAction() = runBlocking {
        // テスト用アラームの挿入
        val alarm = AlarmItemEntity(
            id = "test-alarm-snooze-1",
            title = "定例会議",
            message = "10分後に開始します",
            triggerAtMillis = System.currentTimeMillis() + 1000L
        )
        fakeAlarmDao.insertAlarm(alarm)

        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SNOOZE
            putExtra(NotificationHelper.EXTRA_ALARM_ID, "test-alarm-snooze-1")
            putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 202)
        }

        val receiver = NotificationActionReceiver(
            todoDaoProvider = { fakeTodoDao },
            historyDaoProvider = { fakeHistoryDao },
            alarmDaoProvider = { fakeAlarmDao },
            schedulerProvider = { fakeScheduler }
        )
        receiver.onReceive(context, intent)

        kotlinx.coroutines.delay(200)

        // 延期アラームが DB にスケジュール登録されていること
        val alarms = fakeAlarmDao.getAllAlarms()
        assertTrue(alarms.any { it.title.contains("延期") })
        assertEquals(1, fakeScheduler.scheduledAlarms.size)
    }

    @Test
    fun testNotificationActionReceiverSnoozeActionFailClosedWhenAlarmScheduleFails() = runBlocking {
        fakeScheduler.shouldFail = true

        val alarm = AlarmItemEntity(
            id = "test-alarm-snooze-fail",
            title = "失敗するアラーム",
            message = "権限不足テスト",
            triggerAtMillis = System.currentTimeMillis() + 1000L
        )
        fakeAlarmDao.insertAlarm(alarm)

        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SNOOZE
            putExtra(NotificationHelper.EXTRA_ALARM_ID, "test-alarm-snooze-fail")
            putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 203)
        }

        val receiver = NotificationActionReceiver(
            todoDaoProvider = { fakeTodoDao },
            historyDaoProvider = { fakeHistoryDao },
            alarmDaoProvider = { fakeAlarmDao },
            schedulerProvider = { fakeScheduler }
        )
        receiver.onReceive(context, intent)

        kotlinx.coroutines.delay(200)

        // スケジュール失敗時は新しいアラームがDBに登録されないこと（幽霊アラームの防止）
        val alarms = fakeAlarmDao.getAllAlarms()
        assertEquals(1, alarms.size)
        assertEquals("test-alarm-snooze-fail", alarms[0].id)
        assertEquals(0, fakeScheduler.scheduledAlarms.size)
    }
}
