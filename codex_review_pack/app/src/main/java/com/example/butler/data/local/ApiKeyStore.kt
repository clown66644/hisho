package com.example.butler.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val API_KEYSTORE_ALIAS = "butler_openai_api_key_v1"
internal const val API_KEY_PREFERENCES_NAME = "device_bound_api_credentials"
internal const val ENCRYPTED_API_KEY_PREFERENCE = "encrypted_openai_api_key_v1"

/** APIキー専用。値を列挙・ログ出力するAPIは提供しない。 */
class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        API_KEY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun saveApiKey(apiKey: CharArray): Boolean {
        require(apiKey.isNotEmpty() && apiKey.none(Char::isWhitespace)) {
            "APIキーの形式が不正です。"
        }

        var encodedBuffer: ByteBuffer? = null
        val plaintext = try {
            val buffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(apiKey))
            encodedBuffer = buffer
            ByteArray(buffer.remaining()).also(buffer::get)
        } finally {
            apiKey.fill('\u0000')
            encodedBuffer?.takeIf(ByteBuffer::hasArray)?.array()?.fill(0)
        }
        try {
            val keyStore = loadKeyStore()
            if (preferences.contains(ENCRYPTED_API_KEY_PREFERENCE) &&
                !keyStore.containsAlias(API_KEYSTORE_ALIAS)
            ) {
                throw DatabaseKeyUnavailableException(
                    "保存済みAPIキーを復号するKeystore鍵がありません。上書き前に復旧または明示削除が必要です。"
                )
            }
            val key = getOrCreateKey(keyStore)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val payload = cipher.iv + cipher.doFinal(plaintext)
            return preferences.edit()
                .putString(
                    ENCRYPTED_API_KEY_PREFERENCE,
                    Base64.encodeToString(payload, Base64.NO_WRAP),
                )
                .commit()
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun readApiKey(): String? {
        val encoded = preferences.getString(ENCRYPTED_API_KEY_PREFERENCE, null) ?: return null
        val keyStore = loadKeyStore()
        val key = keyStore.getKey(API_KEYSTORE_ALIAS, null) as? SecretKey
            ?: throw DatabaseKeyUnavailableException(
                "保存済みAPIキーを復号するKeystore鍵がありません。"
            )
        val plaintext = try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) {
                throw DatabaseKeyUnavailableException("暗号化APIキーデータが破損しています。")
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, GCM_IV_BYTES)),
            )
            cipher.doFinal(payload.copyOfRange(GCM_IV_BYTES, payload.size))
        } catch (error: DatabaseKeyUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw DatabaseKeyUnavailableException("APIキーを復号できません。", error)
        }

        return try {
            plaintext.toString(Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun deleteApiKey(): Boolean {
        if (!preferences.edit().remove(ENCRYPTED_API_KEY_PREFERENCE).commit()) return false
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(API_KEYSTORE_ALIAS)) keyStore.deleteEntry(API_KEYSTORE_ALIAS)
        return true
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(keyStore: KeyStore): SecretKey {
        (keyStore.getKey(API_KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                API_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
