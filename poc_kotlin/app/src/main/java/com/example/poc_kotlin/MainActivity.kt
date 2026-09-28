package com.example.poc_kotlin

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.poc_kotlin.alarm.AlarmScheduleResult
import com.example.poc_kotlin.alarm.ExactAlarmScheduler
import com.example.poc_kotlin.alarm.StoredAlarm
import com.example.poc_kotlin.security.SecureStorage
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var alarmScheduler: ExactAlarmScheduler
    private lateinit var secureStorage: SecureStorage
    private lateinit var status: TextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status.text = if (granted) "通知権限を許可しました" else "通知権限が拒否されました"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmScheduler = ExactAlarmScheduler(this)
        secureStorage = SecureStorage(this)
        status = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnScheduleAlarm).setOnClickListener {
            ensureNotificationPermission()
            val alarm = StoredAlarm(
                id = UUID.randomUUID().toString(),
                triggerAtMillis = System.currentTimeMillis() + 10_000,
                title = "執事からのテスト通知",
                message = "Exact Alarmの発火を確認してください。",
            )
            status.text = when (val result = alarmScheduler.scheduleExactAlarm(alarm)) {
                is AlarmScheduleResult.Scheduled -> "予約を受け付けました（発火は未確認）"
                is AlarmScheduleResult.ExactAlarmPermissionRequired -> {
                    openExactAlarmSettings()
                    "正確なアラームの許可が必要です"
                }
                is AlarmScheduleResult.Failed -> "予約に失敗しました: ${result.reason}"
            }
        }

        findViewById<Button>(R.id.btnSaveSecure).setOnClickListener {
            val testValue = "non-secret-test-value"
            secureStorage.saveEncryptedString("storage_probe", testValue)
            status.text = if (secureStorage.getEncryptedString("storage_probe") == testValue) {
                "暗号化ストレージの往復を確認しました（値は非表示）"
            } else {
                "暗号化ストレージの確認に失敗しました"
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }
}
