package com.example.butler.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.entity.TodoEntity
import com.example.butler.data.local.security.DatabasePassphraseProvider
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [TodoEntity::class, OperationHistoryEntity::class, AlarmItemEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao
    abstract fun operationHistoryDao(): OperationHistoryDao
    abstract fun alarmDao(): AlarmDao

    companion object {
        const val DATABASE_NAME = "encrypted_butler.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
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
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `alarms` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `triggerAtMillis` INTEGER NOT NULL,
                        `isFired` INTEGER NOT NULL,
                        `isCancelled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            INSTANCE?.let { return it }
            val passphrase = DatabasePassphraseProvider(context.applicationContext).getOrGeneratePassphrase()
            return getInstance(context.applicationContext, passphrase)
        }

        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context.applicationContext)
                val factory = SupportFactory(passphrase, null, false)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        internal fun resetForTest() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
