package com.example.butler.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

class DatabaseKeyCorruptedException(message: String = "データベースの暗号化鍵に不整合が検出されました。データ保護のため処理を停止します。") : Exception(message)

class DatabasePassphraseProvider(private val context: Context) {

    private val prefFileName = "secret_db_key_prefs"
    private val keyAlias = "butler_db_passphrase_key"

    private fun getEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            prefFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 暗号化DB用 32バイト (256ビット) パスフレーズの取得または初回生成
     */
    fun getOrGeneratePassphrase(): ByteArray {
        return try {
            val prefs = getEncryptedPreferences()
            val existingBase64 = prefs.getString(keyAlias, null)

            if (existingBase64 != null) {
                val decoded = Base64.getDecoder().decode(existingBase64)
                if (decoded.size != 32) {
                    // 鍵サイズ不整合・破損時は既存データを無断上書きせず例外発生
                    throw DatabaseKeyCorruptedException()
                }
                decoded
            } else {
                // 初回生成: 32バイトのランダムパスフレーズ
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                val base64Encoded = Base64.getEncoder().encodeToString(randomBytes)
                prefs.edit().putString(keyAlias, base64Encoded).apply()
                randomBytes
            }
        } catch (e: DatabaseKeyCorruptedException) {
            throw e
        } catch (e: Exception) {
            // 例外メッセージやログにパスフレーズや鍵が含まれないよう一般化
            throw DatabaseKeyCorruptedException("暗号化ストレージへのアクセスに失敗しました。")
        }
    }
}
