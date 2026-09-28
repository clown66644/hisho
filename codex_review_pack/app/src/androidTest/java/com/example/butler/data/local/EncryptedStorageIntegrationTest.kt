package com.example.butler.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.domain.model.TodoItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.security.KeyStore
import net.sqlcipher.database.SQLiteDatabase as CipherDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [TodoEntity::class], version = 1, exportSchema = false)
abstract class LegacyAppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

@RunWith(AndroidJUnit4::class)
class EncryptedStorageIntegrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearTestStorage()
    }

    @After
    fun tearDown() {
        clearTestStorage()
    }

    @Test
    fun sqlCipherDatabaseCannotBeReadAsPlainSQLite() {
        runBlocking {
            val database = openDatabase()
            database.todoDao().upsertTodo(todo("encrypted-row", "暗号化確認"))
            AppDatabase.resetForTest()
            val databaseFile = context.getDatabasePath(ENCRYPTED_DATABASE_NAME)
            assertTrue(databaseFile.exists())

            val header = ByteArray(SQLITE_HEADER.size)
            FileInputStream(databaseFile).use { input ->
                assertEquals(header.size, input.read(header))
            }
            assertFalse(header.contentEquals(SQLITE_HEADER))

            assertThrows(Exception::class.java) {
                val plainDatabase = SQLiteDatabase.openDatabase(
                    databaseFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
                try {
                    plainDatabase.rawQuery(
                        "SELECT name FROM sqlite_master",
                        null,
                    ).use { cursor -> cursor.moveToFirst() }
                } finally {
                    plainDatabase.close()
                }
            }
        }
    }

    @Test
    fun sameDatabaseCanBeDecryptedAfterRestart() {
        runBlocking {
            val database = openDatabase()
            database.todoDao().upsertTodo(todo("restart-row", "再起動前"))
            AppDatabase.resetForTest()

            val reopened = openDatabase()
            val restored = reopened.todoDao().getTodoById("restart-row")

            assertNotNull(restored)
            assertEquals("再起動前", restored?.title)
        }
    }

    @Test
    fun missingEncryptedPassphraseDoesNotReplaceKeyForExistingDatabase() {
        runBlocking {
            val database = openDatabase()
            database.todoDao().upsertTodo(todo("preserved-row", "保全対象"))
            AppDatabase.resetForTest()
        }
        val databaseFile = context.getDatabasePath(ENCRYPTED_DATABASE_NAME)
        val originalLength = databaseFile.length()
        val preferences = databaseKeyPreferences()
        assertTrue(preferences.edit().remove(ENCRYPTED_DATABASE_PASSPHRASE_KEY).commit())

        assertThrows(DatabaseKeyUnavailableException::class.java) {
            AndroidKeystoreDatabasePassphraseProvider(context).getPassphrase()
        }

        assertFalse(preferences.contains(ENCRYPTED_DATABASE_PASSPHRASE_KEY))
        assertEquals(originalLength, databaseFile.length())
        assertTrue(keyStore().containsAlias(DATABASE_WRAPPING_KEY_ALIAS))
    }

    @Test
    fun encryptedVersionOneDatabaseMigratesWithoutDataLoss() {
        val provider = AndroidKeystoreDatabasePassphraseProvider(context)
        val passphrase = provider.getPassphrase()
        CipherDatabase.loadLibs(context)
        val legacy = Room.databaseBuilder(
            context,
            LegacyAppDatabase::class.java,
            ENCRYPTED_DATABASE_NAME,
        )
            .openHelperFactory(SupportFactory(passphrase, null, true))
            .build()
        runBlocking {
            legacy.todoDao().upsertTodo(todo("migration-row", "移行前データ"))
        }
        legacy.close()
        AppDatabase.resetForTest()

        val migrated = openDatabase()
        val restored = runBlocking {
            migrated.todoDao().getTodoById("migration-row")
        }

        assertNotNull(restored)
        assertEquals("移行前データ", restored?.title)
    }

    @Test
    fun supportFactoryClearsPassphraseArrayAfterDatabaseOpen() {
        runBlocking {
            val supplied = ByteArray(32) { (it + 1).toByte() }
            val expectedBeforeOpen = supplied.copyOf()
            val database = AppDatabase.getInstance(context, DatabasePassphraseProvider { supplied })
            assertArrayEquals(expectedBeforeOpen, supplied)

            database.todoDao().getAllTodos()

            assertTrue(supplied.all { it == 0.toByte() })
        }
    }

    @Test
    fun corruptedEncryptedPassphraseIsNotOverwritten() {
        val provider = AndroidKeystoreDatabasePassphraseProvider(context)
        provider.getPassphrase().fill(0)
        val preferences = databaseKeyPreferences()
        val corrupted = "corrupted-payload-that-must-remain"
        assertTrue(
            preferences.edit()
                .putString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, corrupted)
                .commit()
        )

        assertThrows(DatabaseKeyUnavailableException::class.java) {
            provider.getPassphrase()
        }

        assertEquals(
            corrupted,
            preferences.getString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, null),
        )
    }

    @Test
    fun missingKeystoreKeyDoesNotOverwriteEncryptedPassphrase() {
        val provider = AndroidKeystoreDatabasePassphraseProvider(context)
        provider.getPassphrase().fill(0)
        val preferences = databaseKeyPreferences()
        val encryptedBefore = preferences.getString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, null)
        assertNotNull(encryptedBefore)
        deleteKey(DATABASE_WRAPPING_KEY_ALIAS)

        assertThrows(DatabaseKeyUnavailableException::class.java) {
            provider.getPassphrase()
        }

        assertEquals(
            encryptedBefore,
            preferences.getString(ENCRYPTED_DATABASE_PASSPHRASE_KEY, null),
        )
        assertFalse(keyStore().containsAlias(DATABASE_WRAPPING_KEY_ALIAS))
    }

    @Test
    fun todoUpsertUpdatesSingleEncryptedRow() {
        runBlocking {
            val database = openDatabase()
            database.todoDao().upsertTodo(todo("upsert-row", "更新前"))
            database.todoDao().upsertTodo(todo("upsert-row", "更新後"))

            val all = database.todoDao().getAllTodos()

            assertEquals(1, all.count { it.id == "upsert-row" })
            assertEquals("更新後", database.todoDao().getTodoById("upsert-row")?.title)
        }
    }

    @Test
    fun apiKeyIsEncryptedAndInputBufferIsCleared() {
        val marker = "instrumentation-credential-marker"
        val input = marker.toCharArray()
        val store = ApiKeyStore(context)

        assertTrue(store.saveApiKey(input))

        assertTrue(input.all { it == '\u0000' })
        val encoded = context.getSharedPreferences(API_KEY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(ENCRYPTED_API_KEY_PREFERENCE, null)
        assertNotNull(encoded)
        assertFalse(encoded!!.contains(marker))
        assertEquals(marker, store.readApiKey())
    }

    @Test
    fun backupAndDeviceTransferAreDisabled() {
        val info = context.applicationInfo
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertAllStorageDomainsExcluded("backup_rules")
        assertAllStorageDomainsExcluded("data_extraction_rules")
    }

    private fun openDatabase(): AppDatabase {
        val database = AppDatabase.getInstance(context)
        // Roomは遅延オープンするため、呼出側で明示的にDBアクセスして開かせる。
        runBlocking { database.todoDao().getAllTodos() }
        return database
    }

    private fun todo(id: String, title: String): TodoEntity =
        TodoEntity.fromDomainModel(TodoItem(id = id, title = title))

    private fun databaseKeyPreferences() = context.getSharedPreferences(
        DATABASE_KEY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun clearTestStorage() {
        AppDatabase.resetForTest()
        context.deleteDatabase(ENCRYPTED_DATABASE_NAME)
        databaseKeyPreferences().edit().clear().commit()
        context.getSharedPreferences(API_KEY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        deleteKey(DATABASE_WRAPPING_KEY_ALIAS)
        deleteKey(API_KEYSTORE_ALIAS)
    }

    private fun deleteKey(alias: String) {
        keyStore().apply {
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun assertAllStorageDomainsExcluded(resourceName: String) {
        val resourceId = context.resources.getIdentifier(
            resourceName,
            "xml",
            context.packageName,
        )
        assertNotEquals(0, resourceId)
        val excludedDomains = mutableSetOf<String>()
        context.resources.getXml(resourceId).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    parser.getAttributeValue(null, "domain")?.let(excludedDomains::add)
                }
                parser.next()
            }
        }
        assertTrue(excludedDomains.containsAll(REQUIRED_BACKUP_DOMAINS))
    }

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val REQUIRED_BACKUP_DOMAINS =
            setOf("root", "file", "database", "sharedpref", "external")
    }
}
