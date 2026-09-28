package com.example.butler.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val DATABASE_WRAPPING_KEY_ALIAS = "butler_database_wrapping_key_v1"
internal const val DATABASE_KEY_PREFERENCES_NAME = "device_bound_database_key"
internal const val ENCRYPTED_DATABASE_PASSPHRASE_KEY = "encrypted_passphrase_v1"
internal const val ENCRYPTED_DATABASE_NAME = "encrypted_butler.db"

fun interface DatabasePassphraseProvider {
    /** 呼び出し側が使用後にゼロクリアできる、新しい配列を返す。 */
    fun getPassphrase(): ByteArray
}

class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * ランダムなDBパスフレーズをAndroid Keystore鍵でAES-GCM暗号化して保持する。
 * 暗号文だけが復元されKeystore鍵が失われた場合、新規鍵で上書きせず安全に停止する。
 */
class AndroidKeystoreDatabasePassphraseProvider(context: Context) : DatabasePassphraseProvider {
    private val databaseFile = context.applicationContext.getDatabasePath(ENCRYPTED_DATABASE_NAME)
    private val preferences = context.applicationContext.getSharedPreferences(
        DATABASE_KEY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun getPassphrase(): ByteArray {
        val encrypted = preferences.getString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, null)
        val keyStore = loadKeyStore()

        if (encrypted == null && databaseFile.exists() && databaseFile.length() > 0L) {
            throw DatabaseKeyUnavailableException(
                "既存の暗号化DBに対応する暗号化鍵データがありません。" +
                    "DBを上書きせず復旧処理が必要です。"
            )
        }

        if (encrypted != null && !keyStore.containsAlias(DATABASE_WRAPPING_KEY_ALIAS)) {
            throw DatabaseKeyUnavailableException(
                "暗号化DB鍵を復号するKeystore鍵がありません。データを上書きせず復旧処理が必要です。"
            )
        }

        val wrappingKey = if (keyStore.containsAlias(DATABASE_WRAPPING_KEY_ALIAS)) {
            keyStore.getKey(DATABASE_WRAPPING_KEY_ALIAS, null) as? SecretKey
                ?: throw DatabaseKeyUnavailableException("Keystore鍵の形式が不正です。")
        } else {
            generateWrappingKey()
        }

        return if (encrypted == null) {
            createAndStorePassphrase(wrappingKey)
        } else {
            decryptPassphrase(wrappingKey, encrypted)
        }
    }

    private fun createAndStorePassphrase(wrappingKey: SecretKey): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
            val payload = cipher.iv + cipher.doFinal(passphrase)
            val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
            if (!preferences.edit().putString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, encoded).commit()) {
                throw DatabaseKeyUnavailableException("暗号化DB鍵を安全に保存できませんでした。")
            }
            return passphrase.copyOf()
        } finally {
            passphrase.fill(0)
        }
    }

    private fun decryptPassphrase(wrappingKey: SecretKey, encoded: String): ByteArray {
        try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) {
                throw DatabaseKeyUnavailableException("暗号化DB鍵データが破損しています。")
            }
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext).also { passphrase ->
                if (passphrase.size != PASSPHRASE_BYTES) {
                    passphrase.fill(0)
                    throw DatabaseKeyUnavailableException("復号したDB鍵の長さが不正です。")
                }
            }
        } catch (error: DatabaseKeyUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw DatabaseKeyUnavailableException(
                "暗号化DB鍵を復号できません。データを上書きせず復旧処理が必要です。",
                error,
            )
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun generateWrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                DATABASE_WRAPPING_KEY_ALIAS,
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
        const val PASSPHRASE_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
