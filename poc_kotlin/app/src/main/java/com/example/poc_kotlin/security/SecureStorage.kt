package com.example.poc_kotlin.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_butler_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveEncryptedString(key: String, value: String) {
        check(sharedPreferences.edit().putString(key, value).commit()) {
            "暗号化ストレージへの保存に失敗しました"
        }
    }

    fun getEncryptedString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun remove(key: String) {
        check(sharedPreferences.edit().remove(key).commit()) {
            "暗号化ストレージからの削除に失敗しました"
        }
    }
}
