package com.example.butler.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.entity.TodoEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [TodoEntity::class, OperationHistoryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun operationHistoryDao(): OperationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(
            context: Context,
            passphraseProvider: DatabasePassphraseProvider =
                AndroidKeystoreDatabasePassphraseProvider(context),
        ): AppDatabase {
            INSTANCE?.let { return it }

            return synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, passphraseProvider)
                    .also { INSTANCE = it }
            }
        }

        private fun buildDatabase(
            context: Context,
            passphraseProvider: DatabasePassphraseProvider,
        ): AppDatabase {
            val passphrase = passphraseProvider.getPassphrase()
            require(passphrase.isNotEmpty()) { "DBパスフレーズが空です。" }

            return try {
                SQLiteDatabase.loadLibs(context)
                // clearPassphrase=trueにより、初回オープン後にSupportFactoryが配列をゼロクリアする。
                val factory = SupportFactory(passphrase, null, true)
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    ENCRYPTED_DATABASE_NAME,
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
            } catch (error: Exception) {
                passphrase.fill(0)
                throw error
            }
        }

        internal fun resetForTest() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `operation_histories` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `actor` TEXT NOT NULL,
                        `userInput` TEXT,
                        `aiInterpretation` TEXT,
                        `actionType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `previousStateJson` TEXT,
                        `newStateJson` TEXT,
                        `status` TEXT NOT NULL,
                        `isUndone` INTEGER NOT NULL,
                        `isRedoable` INTEGER NOT NULL,
                        `lastPhase` TEXT NOT NULL,
                        `lastResult` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `compensationResult` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `operation_histories` " +
                        "ADD COLUMN `operationOrder` INTEGER NOT NULL DEFAULT 0"
                )
                // 既存行はSQLiteの挿入順を使って、従来より正確なUndo順を保持する。
                db.execSQL(
                    "UPDATE `operation_histories` SET `operationOrder` = rowid"
                )
            }
        }
    }
}
