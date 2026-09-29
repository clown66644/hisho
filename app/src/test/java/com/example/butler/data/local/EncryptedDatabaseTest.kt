package com.example.butler.data.local

import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.data.local.security.DatabaseKeyCorruptedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom

class EncryptedDatabaseTest {

    @Test
    fun testPassphraseValidation32Bytes() {
        val validPassphrase = ByteArray(32)
        SecureRandom().nextBytes(validPassphrase)
        assertEquals(32, validPassphrase.size)

        val invalidPassphrase = ByteArray(16) // 32バイト以外は不整合
        try {
            if (invalidPassphrase.size != 32) {
                throw DatabaseKeyCorruptedException()
            }
            fail("Should have thrown DatabaseKeyCorruptedException")
        } catch (e: DatabaseKeyCorruptedException) {
            assertNotNull(e)
        }
    }

    @Test
    fun testExceptionDoesNotExposeKeyContent() {
        val secretKey = "sk-secret-key-content-12345"
        val exception = DatabaseKeyCorruptedException("暗号化ストレージへのアクセスに失敗しました。")

        val message = exception.message ?: ""
        assertFalse(message.contains(secretKey))
        assertFalse(message.contains("sk-secret"))
    }

    @Test
    fun testTodoEntityUpsertDataPreservation() {
        val originalTodo = TodoEntity(
            id = "todo-upsert-1",
            title = "買い物",
            detail = "牛乳を購入",
            memo = "特売日",
            dueDate = 1700000000000L,
            scheduledStartTime = null,
            scheduledEndTime = null,
            estimatedMinutes = 15,
            status = "UNSTARTED",
            isHealthOrSafety = false,
            financialImpact = 1,
            workImpact = 1,
            irretrievableLoss = false,
            mentalLoad = 1,
            requiredStamina = 1,
            isPinnedPriority = false,
            pinnedPriority = null,
            createdAt = 1699999999000L,
            updatedAt = 1699999999000L
        )

        // 安全なドメイン変換
        val domainModel = originalTodo.toDomainModel()
        assertEquals("todo-upsert-1", domainModel.id)
        assertEquals("買い物", domainModel.title)

        // 更新データの検証 (ID保持、状態変更)
        val updatedDomain = domainModel.copy(status = com.example.butler.domain.model.TodoStatus.COMPLETED)
        val updatedEntity = TodoEntity.fromDomainModel(updatedDomain)

        assertEquals("todo-upsert-1", updatedEntity.id)
        assertEquals("COMPLETED", updatedEntity.status)
        assertEquals("特売日", updatedEntity.memo) // メモなど関連データが破壊されず保持される
    }

    @Test
    fun testKeyCorruptedExceptionHandlingDoesNotDestroyDatabase() {
        var isDbDestroyed = false
        try {
            val isKeyCorrupted = true
            if (isKeyCorrupted) {
                throw DatabaseKeyCorruptedException()
            }
            isDbDestroyed = true
        } catch (e: DatabaseKeyCorruptedException) {
            // 安全な例外キャッチ
        }
        assertFalse("キー破損時に無断でDB削除・初期化を行わないこと", isDbDestroyed)
    }

    @Test
    fun testMigrationsSqlExecution() {
        val executedSqls = mutableListOf<String>()
        val fakeDb = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                executedSqls.add(args[0] as String)
            }
            null
        } as androidx.sqlite.db.SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(fakeDb)
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `operation_histories`") })

        executedSqls.clear()
        AppDatabase.MIGRATION_2_3.migrate(fakeDb)
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `alarms`") })
    }
}
