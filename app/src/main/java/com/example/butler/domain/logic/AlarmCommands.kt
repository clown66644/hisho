package com.example.butler.domain.logic

import com.example.butler.alarm.AlarmScheduler
import com.example.butler.alarm.ScheduleResult
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.domain.model.OperationHistory

class ScheduleAlarmCommand(
    override val history: OperationHistory,
    val alarmId: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    private val scheduler: AlarmScheduler,
    private val alarmDao: AlarmDao? = null,
    private val inMemoryAlarms: MutableMap<String, AlarmItemEntity>? = null
) : Command {

    override suspend fun execute(): Boolean {
        // 1. OS アラーム予約
        val scheduleResult = scheduler.scheduleExactAlarm(
            alarmId = alarmId,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis
        )

        if (scheduleResult !is ScheduleResult.Scheduled) {
            // OS予約失敗時はDB保存・成功処理を行わない
            return false
        }

        // 2. DB 保存 (アトミック補償付き)
        val entity = AlarmItemEntity(
            id = alarmId,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis,
            isFired = false,
            isCancelled = false
        )

        val dbSuccess = if (alarmDao != null) {
            try {
                alarmDao.insertAlarm(entity)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

        if (!dbSuccess) {
            // DB保存失敗時はOS予約を補償キャンセル
            scheduler.cancelAlarm(alarmId)
            return false
        }

        inMemoryAlarms?.put(alarmId, entity)
        return true
    }

    override suspend fun undo(): Boolean {
        // OS アラームキャンセル
        scheduler.cancelAlarm(alarmId)

        // DB 更新
        if (alarmDao != null) {
            try {
                val existing = alarmDao.getAlarmById(alarmId)
                if (existing != null) {
                    alarmDao.updateAlarm(existing.copy(isCancelled = true))
                }
            } catch (e: Exception) {
                return false
            }
        }
        inMemoryAlarms?.get(alarmId)?.let {
            inMemoryAlarms[alarmId] = it.copy(isCancelled = true)
        }
        return true
    }
}

class CancelAlarmCommand(
    override val history: OperationHistory,
    val alarmId: String,
    private val scheduler: AlarmScheduler,
    private val alarmDao: AlarmDao? = null,
    private val inMemoryAlarms: MutableMap<String, AlarmItemEntity>? = null
) : Command {

    private var previousEntity: AlarmItemEntity? = null

    override suspend fun execute(): Boolean {
        scheduler.cancelAlarm(alarmId)

        if (alarmDao != null) {
            try {
                previousEntity = alarmDao.getAlarmById(alarmId)
                previousEntity?.let {
                    alarmDao.updateAlarm(it.copy(isCancelled = true))
                }
            } catch (e: Exception) {
                return false
            }
        }
        inMemoryAlarms?.get(alarmId)?.let {
            previousEntity = it
            inMemoryAlarms[alarmId] = it.copy(isCancelled = true)
        }
        return true
    }

    override suspend fun undo(): Boolean {
        val target = previousEntity ?: return false
        val result = scheduler.scheduleExactAlarm(
            alarmId = target.id,
            title = target.title,
            message = target.message,
            triggerAtMillis = target.triggerAtMillis
        )

        if (result !is ScheduleResult.Scheduled) return false

        if (alarmDao != null) {
            try {
                alarmDao.updateAlarm(target.copy(isCancelled = false))
            } catch (e: Exception) {
                scheduler.cancelAlarm(target.id)
                return false
            }
        }
        inMemoryAlarms?.put(target.id, target.copy(isCancelled = false))
        return true
    }
}
