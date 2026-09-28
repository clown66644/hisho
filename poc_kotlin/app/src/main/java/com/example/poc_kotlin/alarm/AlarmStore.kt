package com.example.poc_kotlin.alarm

import android.content.Context
import com.example.poc_kotlin.security.SecureStorage
import org.json.JSONArray
import org.json.JSONObject

data class StoredAlarm(
    val id: String,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
)

/** 暗号化ストレージに、再起動後に復元すべき未発火アラームだけを保持する。 */
class AlarmStore(context: Context) {
    private val storage = SecureStorage(context.applicationContext)

    @Synchronized
    fun upsert(alarm: StoredAlarm) {
        val alarms = readAll().associateBy { it.id }.toMutableMap()
        alarms[alarm.id] = alarm
        writeAll(alarms.values.sortedBy { it.triggerAtMillis })
    }

    @Synchronized
    fun remove(id: String) {
        writeAll(readAll().filterNot { it.id == id })
    }

    @Synchronized
    fun readAll(): List<StoredAlarm> {
        val raw = storage.getEncryptedString(STORAGE_KEY) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredAlarm(
                            id = item.getString("id"),
                            triggerAtMillis = item.getLong("triggerAtMillis"),
                            title = item.getString("title"),
                            message = item.getString("message"),
                        )
                    )
                }
            }
        } catch (error: Exception) {
            // 破損を空データとして扱うと、次の保存で復旧対象を失うため安全側に失敗させる。
            throw IllegalStateException("保存済みアラームを読み取れません", error)
        }
    }

    private fun writeAll(alarms: Collection<StoredAlarm>) {
        if (alarms.isEmpty()) {
            storage.remove(STORAGE_KEY)
            return
        }
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject()
                    .put("id", alarm.id)
                    .put("triggerAtMillis", alarm.triggerAtMillis)
                    .put("title", alarm.title)
                    .put("message", alarm.message)
            )
        }
        storage.saveEncryptedString(STORAGE_KEY, array.toString())
    }

    private companion object {
        const val STORAGE_KEY = "pending_alarms_v1"
    }
}
