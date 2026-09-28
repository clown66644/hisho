package com.example.butler.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.butler.R
import com.example.butler.alarm.AlarmScheduler
import com.example.butler.data.remote.PersonaType
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel = MainViewModel()
    private lateinit var adapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        setupControls()
        observeUiState()
        checkExactAlarmPermission()
    }

    private fun setupRecyclerView() {
        val rvCards: RecyclerView = findViewById(R.id.rvCards)
        adapter = CardAdapter(
            onApproveClicked = { cardId ->
                lifecycleScope.launch {
                    viewModel.approveConfirmationCard(cardId)
                }
            },
            onRejectClicked = { cardId ->
                viewModel.rejectConfirmationCard(cardId)
            }
        )
        rvCards.layoutManager = LinearLayoutManager(this)
        rvCards.adapter = adapter
    }

    private fun setupControls() {
        val etMessageInput: EditText = findViewById(R.id.etMessageInput)
        val btnSend: Button = findViewById(R.id.btnSend)
        val btnUndo: Button = findViewById(R.id.btnUndo)
        val btnRedo: Button = findViewById(R.id.btnRedo)
        val rgPersonaSelector: RadioGroup = findViewById(R.id.rgPersonaSelector)

        rgPersonaSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPersonaButler -> viewModel.changePersona(PersonaType.BUTLER)
                R.id.rbPersonaSecretary -> viewModel.changePersona(PersonaType.SECRETARY)
                R.id.rbPersonaCoach -> viewModel.changePersona(PersonaType.COACH)
            }
        }

        btnSend.setOnClickListener {
            val text = etMessageInput.text.toString().trim()
            if (text.isNotBlank()) {
                viewModel.handleAiJsonInput(text)
                etMessageInput.setText("")
            }
        }

        btnUndo.setOnClickListener {
            lifecycleScope.launch {
                viewModel.undo()
            }
        }

        btnRedo.setOnClickListener {
            lifecycleScope.launch {
                viewModel.redo()
            }
        }
    }

    private fun observeUiState() {
        val btnUndo: Button = findViewById(R.id.btnUndo)
        val btnRedo: Button = findViewById(R.id.btnRedo)
        val tvHeaderTitle: TextView = findViewById(R.id.tvHeaderTitle)
        val rbPersonaButler: RadioButton = findViewById(R.id.rbPersonaButler)
        val rbPersonaSecretary: RadioButton = findViewById(R.id.rbPersonaSecretary)
        val rbPersonaCoach: RadioButton = findViewById(R.id.rbPersonaCoach)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.cards)
                    btnUndo.isEnabled = state.canUndo
                    btnRedo.isEnabled = state.canRedo

                    tvHeaderTitle.text = when (state.currentPersona) {
                        PersonaType.BUTLER -> "AI 執事ダッシュボード"
                        PersonaType.SECRETARY -> "AI 秘書ダッシュボード"
                        PersonaType.COACH -> "AI コーチダッシュボード"
                        PersonaType.MAID -> "AI メイドダッシュボード"
                    }

                    when (state.currentPersona) {
                        PersonaType.BUTLER -> if (!rbPersonaButler.isChecked) rbPersonaButler.isChecked = true
                        PersonaType.SECRETARY -> if (!rbPersonaSecretary.isChecked) rbPersonaSecretary.isChecked = true
                        PersonaType.COACH -> if (!rbPersonaCoach.isChecked) rbPersonaCoach.isChecked = true
                        PersonaType.MAID -> if (!rbPersonaButler.isChecked) rbPersonaButler.isChecked = true
                    }

                    state.statusMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkExactAlarmPermission() {
        val scheduler = AlarmScheduler(this)
        if (!scheduler.canScheduleExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertDialog.Builder(this)
                .setTitle("高精度アラーム権限が必要です")
                .setMessage("執事アプリが定刻通りアラームやリマインダーを動かすため、設定画面で「アラームとリマインダー」の権限を許可してください。")
                .setPositiveButton("設定画面を開く") { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
                .setNegativeButton("後で", null)
                .show()
        }
    }
}
