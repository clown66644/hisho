package com.example.butler.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.butler.data.remote.PersonaType

class SettingsManager(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secret_user_settings_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("user_settings_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun getApiKey(): String {
        return prefs.getString("KEY_OPENAI_API", "") ?: ""
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString("KEY_OPENAI_API", apiKey).apply()
    }

    fun getSelectedPersona(): PersonaType {
        val name = prefs.getString("KEY_SELECTED_PERSONA", PersonaType.BUTLER.name)
        return try {
            PersonaType.valueOf(name ?: PersonaType.BUTLER.name)
        } catch (e: Exception) {
            PersonaType.BUTLER
        }
    }

    fun setSelectedPersona(persona: PersonaType) {
        prefs.edit().putString("KEY_SELECTED_PERSONA", persona.name).apply()
    }
}
