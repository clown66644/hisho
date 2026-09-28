package com.example.butler.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.butler.R
import com.example.butler.data.local.AppDatabase
import com.example.butler.domain.logic.PriorityCalculator
import com.example.butler.domain.model.PriorityLevel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var actionInput: EditText
    private lateinit var executeButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var statusText: TextView
    private lateinit var cardContainer: LinearLayout
    private var controller: MainUiController? = null
    private val priorityCalculator = PriorityCalculator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        controller = try {
            MainUiController(AppDatabase.getInstance(applicationContext))
        } catch (_: Exception) {
            showUnavailableState()
            null
        }

        executeButton.setOnClickListener {
            val json = actionInput.text?.toString().orEmpty()
            launchControllerAction { activeController ->
                if (activeController.submitAiAction(json)) actionInput.text?.clear()
            }
        }
        undoButton.setOnClickListener {
            launchControllerAction { activeController -> activeController.undo() }
        }
        redoButton.setOnClickListener {
            launchControllerAction { activeController -> activeController.redo() }
        }

        controller?.let { activeController ->
            lifecycleScope.launch {
                try {
                    activeController.initialize()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showUnavailableState()
                    return@launch
                }
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    activeController.state.collect(::render)
                }
            }
        }
    }

    private fun bindViews() {
        actionInput = findViewById(R.id.actionInput)
        executeButton = findViewById(R.id.executeButton)
        undoButton = findViewById(R.id.undoButton)
        redoButton = findViewById(R.id.redoButton)
        statusText = findViewById(R.id.statusText)
        cardContainer = findViewById(R.id.cardContainer)
    }

    private fun render(state: MainUiState) {
        statusText.text = state.statusMessage
        undoButton.isEnabled = state.canUndo
        redoButton.isEnabled = state.canRedo
        cardContainer.removeAllViews()
        state.todos.forEach { todo ->
            val (_, priority) = priorityCalculator.calculatePriority(todo)
            val rank = when (priority) {
                PriorityLevel.TOP_PRIORITY -> "S"
                PriorityLevel.HIGH -> "A"
                PriorityLevel.MEDIUM -> "B"
                PriorityLevel.LOW -> "C"
            }
            cardContainer.addView(
                MaterialCardView(this).apply {
                    radius = dp(12).toFloat()
                    strokeWidth = dp(1)
                    strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                    setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
                    addView(
                        TextView(context).apply {
                            setPadding(dp(16), dp(14), dp(16), dp(14))
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            textSize = 16f
                            text = buildString {
                                append("[$rank] ")
                                append(todo.title)
                                todo.detail?.takeIf(String::isNotBlank)?.let {
                                    append("\n")
                                    append(it)
                                }
                            }
                        },
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(10) }
                },
            )
        }
    }

    private fun showUnavailableState() {
        statusText.text = getString(R.string.database_unavailable)
        executeButton.isEnabled = false
        undoButton.isEnabled = false
        redoButton.isEnabled = false
    }

    private fun launchControllerAction(
        action: suspend (MainUiController) -> Unit,
    ) {
        val activeController = controller ?: return
        lifecycleScope.launch {
            try {
                action(activeController)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showUnavailableState()
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
