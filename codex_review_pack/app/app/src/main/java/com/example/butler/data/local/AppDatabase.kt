package com.example.butler.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.butler.data.local.dao.AlarmDao
import com.example.butler.data.local.dao.OperationHistoryDao
import com.example.butler.data.local.dao.TodoDao
import com.example.butler.data.local.entity.AlarmItemEntity
import com.example.butler.data.local.entity.OperationHistoryEntity
import com.example.butler.data.local.entity.TodoEntity
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
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "encrypted_butler.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
