package com.example.butler.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.butler.data.local.security.SettingsManager
import com.example.butler.data.remote.PersonaType
import com.example.butler.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsAndPersonaTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    @Test
    fun testSettingsManagerEncryptedSaveAndRetrieve() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)

        assertEquals("", settingsManager.getApiKey())
        assertEquals(PersonaType.BUTLER, settingsManager.getSelectedPersona())

        settingsManager.setApiKey("sk-test-mock-api-key-12345")
        assertEquals("sk-test-mock-api-key-12345", settingsManager.getApiKey())

        settingsManager.setSelectedPersona(PersonaType.SECRETARY)
        assertEquals(PersonaType.SECRETARY, settingsManager.getSelectedPersona())
    }

    @Test
    fun testChangePersonaCommandAndUndo() = runBlocking {
        assertEquals(PersonaType.BUTLER, viewModel.uiState.value.currentPersona)

        viewModel.changePersonaSuspend(PersonaType.SECRETARY)
        assertEquals(PersonaType.SECRETARY, viewModel.uiState.value.currentPersona)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.changePersonaSuspend(PersonaType.COACH)
        assertEquals(PersonaType.COACH, viewModel.uiState.value.currentPersona)

        assertTrue(viewModel.undo())
        assertEquals(PersonaType.SECRETARY, viewModel.uiState.value.currentPersona)

        assertTrue(viewModel.undo())
        assertEquals(PersonaType.BUTLER, viewModel.uiState.value.currentPersona)
    }
}
