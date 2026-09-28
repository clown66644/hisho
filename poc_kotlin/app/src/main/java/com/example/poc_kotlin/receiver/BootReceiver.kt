package com.example.poc_kotlin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.poc_kotlin.alarm.AlarmScheduleResult
import com.example.poc_kotlin.alarm.AlarmStore
import com.example.poc_kotlin.alarm.ExactAlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val store = AlarmStore(context)
        val scheduler = ExactAlarmScheduler(context)
        val now = System.currentTimeMillis()

        store.readAll().forEach { alarm ->
            if (alarm.triggerAtMillis <= now) {
                store.remove(alarm.id)
            } else {
                when (scheduler.scheduleExactAlarm(alarm)) {
                    is AlarmScheduleResult.Scheduled -> Unit
                    is AlarmScheduleResult.ExactAlarmPermissionRequired -> Unit
                    is AlarmScheduleResult.Failed -> Unit
                }
            }
        }
    }
}
