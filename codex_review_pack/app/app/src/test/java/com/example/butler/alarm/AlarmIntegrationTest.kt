package com.example.butler.alarm

import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.domain.logic.CancelAlarmCommand
import com.example.butler.domain.logic.ScheduleAlarmCommand
import com.example.butler.domain.model.Actor
import com.example.butler.domain.model.OperationHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AlarmIntegrationTest {

    private class FakeAlarmScheduler {
        val scheduledAlarms = mutableMapOf<String, Long>()
        var canSchedule = true
        var shouldScheduleFail = false

        fun canScheduleExactAlarms(): Boolean = canSchedule

        fun scheduleExactAlarm(
            alarmId: String,
            title: String,
            message: String,
            triggerAtMillis: Long
        ): ScheduleResult {
            if (!canSchedule) return ScheduleResult.PermissionRequired
            if (shouldScheduleFail) return ScheduleResult.Failed("Test Error")
            scheduledAlarms[alarmId] = triggerAtMillis
            return ScheduleResult.Scheduled
        }

        fun cancelAlarm(alarmId: String) {
            scheduledAlarms.remove(alarmId)
        }
    }

    private class FakeAlarmDao : AlarmDao {
        val db = mutableMapOf<String, AlarmItemEntity>()
        var shouldFailInsert = false

        override suspend fun getAllAlarms(): List<AlarmItemEntity> {
            return db.values.sortedBy { it.triggerAtMillis }
        }

        override suspend fun getAlarmById(id: String): AlarmItemEntity? {
            return db[id]
        }

        override suspend fun getPendingFutureAlarms(currentTime: Long): List<AlarmItemEntity> {
            return db.values.filter { !it.isFired && !it.isCancelled && it.triggerAtMillis > currentTime }
                .sortedBy { it.triggerAtMillis }
        }

        override suspend fun insertAlarm(alarm: AlarmItemEntity) {
            if (shouldFailInsert) throw RuntimeException("DB Insert Error")
            db[alarm.id] = alarm
        }

        override suspend fun updateAlarm(alarm: AlarmItemEntity) {
            db[alarm.id] = alarm
        }

        override suspend fun deleteAlarm(alarm: AlarmItemEntity) {
            db.remove(alarm.id)
        }
    }

    private lateinit var fakeScheduler: FakeAlarmScheduler
    private lateinit var fakeDao: FakeAlarmDao
    private val inMemoryAlarms = mutableMapOf<String, AlarmItemEntity>()

    @Before
    fun setUp() {
        fakeScheduler = FakeAlarmScheduler()
        fakeDao = FakeAlarmDao()
        inMemoryAlarms.clear()
    }

    @Test
    fun testSameTitleSameTimeIndependentAlarms() = runBlocking {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        val title = "病院に行く"
        val triggerTime = System.currentTimeMillis() + 3600000L

        val cmd1 = ScheduleAlarmCommand(
            history = OperationHistory(id = "op-alarm-1", actor = Actor.USER, actionType = "SCHEDULE_ALARM", targetId = id1),
            alarmId = id1,
            title = title,
            message = "診察カード準備",
            triggerAtMillis = triggerTime,
            scheduler = createSchedulerProxy(fakeScheduler),
            alarmDao = fakeDao,
            inMemoryAlarms = inMemoryAlarms
        )

        val cmd2 = ScheduleAlarmCommand(
            history = OperationHistory(id = "op-alarm-2", actor = Actor.USER, actionType = "SCHEDULE_ALARM", targetId = id2),
            alarmId = id2,
            title = title,
            message = "お薬手帳持参",
            triggerAtMillis = triggerTime,
            scheduler = createSchedulerProxy(fakeScheduler),
            alarmDao = fakeDao,
            inMemoryAlarms = inMemoryAlarms
        )

        assertTrue(cmd1.execute())
        assertTrue(cmd2.execute())

        assertEquals(2, fakeScheduler.scheduledAlarms.size)
        assertEquals(2, fakeDao.db.size)

        val cancelCmd1 = CancelAlarmCommand(
            history = OperationHistory(id = "op-cancel-1", actor = Actor.USER, actionType = "CANCEL_ALARM", targetId = id1),
            alarmId = id1,
            scheduler = createSchedulerProxy(fakeScheduler),
            alarmDao = fakeDao,
            inMemoryAlarms = inMemoryAlarms
        )
        assertTrue(cancelCmd1.execute())

        assertEquals(1, fakeScheduler.scheduledAlarms.size)
        assertTrue(fakeScheduler.scheduledAlarms.containsKey(id2))
        assertTrue(fakeDao.db[id1]!!.isCancelled)
        assertFalse(fakeDao.db[id2]!!.isCancelled)
    }

    @Test
    fun testExpiredAndFiredAlarmsExcludedFromRebootRestoration() = runBlocking {
        val now = System.currentTimeMillis()

        val futureAlarm = AlarmItemEntity(id = "a-future", title = "未来予定", message = "発火予定", triggerAtMillis = now + 100000L, isFired = false, isCancelled = false)
        val expiredAlarm = AlarmItemEntity(id = "a-expired", title = "過去予定", message = "期限切れ", triggerAtMillis = now - 100000L, isFired = false, isCancelled = false)
        val firedAlarm = AlarmItemEntity(id = "a-fired", title = "発火済み", message = "完了", triggerAtMillis = now + 100000L, isFired = true, isCancelled = false)
        val cancelledAlarm = AlarmItemEntity(id = "a-cancelled", title = "中止", message = "中止", triggerAtMillis = now + 100000L, isFired = false, isCancelled = true)

        fakeDao.insertAlarm(futureAlarm)
        fakeDao.insertAlarm(expiredAlarm)
        fakeDao.insertAlarm(firedAlarm)
        fakeDao.insertAlarm(cancelledAlarm)

        val pendingList = fakeDao.getPendingFutureAlarms(now)
        assertEquals(1, pendingList.size)
        assertEquals("a-future", pendingList[0].id)
    }

    @Test
    fun testDbSaveFailureTriggersOsCompensationCancel() = runBlocking {
        fakeDao.shouldFailInsert = true
        val id = UUID.randomUUID().toString()

        val cmd = ScheduleAlarmCommand(
            history = OperationHistory(id = "op-alarm-fail", actor = Actor.USER, actionType = "SCHEDULE_ALARM", targetId = id),
            alarmId = id,
            title = "テスト失敗",
            message = "補償確認",
            triggerAtMillis = System.currentTimeMillis() + 60000L,
            scheduler = createSchedulerProxy(fakeScheduler),
            alarmDao = fakeDao,
            inMemoryAlarms = inMemoryAlarms
        )

        val result = cmd.execute()
        assertFalse(result)
        assertFalse(fakeScheduler.scheduledAlarms.containsKey(id))
    }

    @Test
    fun testUndoScheduleAlarmCancelsOsAndDbState() = runBlocking {
        val id = UUID.randomUUID().toString()
        val cmd = ScheduleAlarmCommand(
            history = OperationHistory(id = "op-alarm-undo", actor = Actor.USER, actionType = "SCHEDULE_ALARM", targetId = id),
            alarmId = id,
            title = "Undoテスト",
            message = "メッセージ",
            triggerAtMillis = System.currentTimeMillis() + 60000L,
            scheduler = createSchedulerProxy(fakeScheduler),
            alarmDao = fakeDao,
            inMemoryAlarms = inMemoryAlarms
        )

        assertTrue(cmd.execute())
        assertTrue(fakeScheduler.scheduledAlarms.containsKey(id))

        assertTrue(cmd.undo())
        assertFalse(fakeScheduler.scheduledAlarms.containsKey(id))
        assertTrue(fakeDao.db[id]!!.isCancelled)
    }

    private fun createSchedulerProxy(fake: FakeAlarmScheduler): AlarmScheduler {
        return object : AlarmScheduler(dummyContext()) {
            override fun canScheduleExactAlarms(): Boolean = fake.canScheduleExactAlarms()
            override fun scheduleExactAlarm(alarmId: String, title: String, message: String, triggerAtMillis: Long): ScheduleResult {
                return fake.scheduleExactAlarm(alarmId, title, message, triggerAtMillis)
            }
            override fun cancelAlarm(alarmId: String) {
                fake.cancelAlarm(alarmId)
            }
        }
    }

    private fun dummyContext(): android.content.Context {
        return androidx.test.core.app.ApplicationProvider.getApplicationContext()
    }
}
