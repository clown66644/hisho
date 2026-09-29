package com.example.butler.data.local

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.butler.domain.model.CalendarEvent
import java.util.TimeZone

class CalendarSyncManager(
    private val context: Context? = null,
    private val inMemoryEvents: MutableList<CalendarEvent>? = null
) {

    fun hasReadPermission(): Boolean {
        if (context == null) return inMemoryEvents != null
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWritePermission(): Boolean {
        if (context == null) return inMemoryEvents != null
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 指定期間の予定一覧を取得する。権限がない場合は例外を投げず安全に空リストを返す。
     */
    fun getEvents(startTime: Long, endTime: Long): List<CalendarEvent> {
        if (inMemoryEvents != null) {
            return inMemoryEvents.filter {
                (it.startTime in startTime..endTime) || (it.endTime in startTime..endTime) ||
                        (it.startTime <= startTime && it.endTime >= endTime)
            }
        }

        if (!hasReadPermission() || context == null) {
            return emptyList()
        }

        val eventsList = mutableListOf<CalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        )

        val selection = "(${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DTEND} >= ?)"
        val selectionArgs = arrayOf(endTime.toString(), startTime.toString())

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx).toString()
                    val title = it.getString(titleIdx) ?: "無題"
                    val dtStart = it.getLong(startIdx)
                    val dtEnd = it.getLong(endIdx)
                    val loc = it.getString(locIdx)
                    val isAllDay = it.getInt(allDayIdx) == 1

                    eventsList.add(
                        CalendarEvent(
                            id = id,
                            googleEventId = id,
                            title = title,
                            startTime = dtStart,
                            endTime = dtEnd,
                            location = loc,
                            isAllDay = isAllDay
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return eventsList
    }

    /**
     * ProviderのイベントIDから完全な予定スナップショットを取得する。
     */
    fun getEventById(eventId: String): CalendarEvent? {
        if (inMemoryEvents != null) {
            return inMemoryEvents.find { it.id == eventId || it.googleEventId == eventId }
        }

        if (!hasReadPermission() || context == null) {
            return null
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        )

        return try {
            val eventIdLong = eventId.toLongOrNull() ?: return null
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventIdLong)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                    val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                    val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                    val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)

                    val id = it.getLong(idIdx).toString()
                    val title = it.getString(titleIdx) ?: "無題"
                    val dtStart = it.getLong(startIdx)
                    val dtEnd = it.getLong(endIdx)
                    val loc = it.getString(locIdx)
                    val isAllDay = it.getInt(allDayIdx) == 1

                    CalendarEvent(
                        id = id,
                        googleEventId = id,
                        title = title,
                        startTime = dtStart,
                        endTime = dtEnd,
                        location = loc,
                        isAllDay = isAllDay
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 書き込み可能なカレンダーIDをクエリして返す。見つからない場合はフォールバックとして 1L を返す。
     */
    fun getWritableCalendarId(): Long {
        if (context == null || !hasReadPermission()) return 1L

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    it.getLong(idIdx)
                } else {
                    1L
                }
            } ?: 1L
        } catch (e: Exception) {
            1L
        }
    }

    /**
     * 予定をカレンダーに追加する。
     * @return 成功時はイベントID、失敗時は null
     */
    fun insertEvent(event: CalendarEvent, calendarId: Long? = null): String? {
        val targetCalendarId = calendarId ?: getWritableCalendarId()
        if (inMemoryEvents != null) {
            inMemoryEvents.removeIf { it.id == event.id }
            inMemoryEvents.add(event)
            return event.id
        }

        if (!hasWritePermission() || context == null) {
            return null
        }

        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 予定を更新する。
     */
    fun updateEvent(event: CalendarEvent): Boolean {
        if (inMemoryEvents != null) {
            val idx = inMemoryEvents.indexOfFirst { it.id == event.id }
            if (idx >= 0) {
                inMemoryEvents[idx] = event
                return true
            }
            inMemoryEvents.add(event)
            return true
        }

        if (!hasWritePermission() || context == null) {
            return false
        }

        val eventId = event.googleEventId ?: event.id
        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 予定を削除する。
     */
    fun deleteEvent(eventId: String): Boolean {
        if (inMemoryEvents != null) {
            return inMemoryEvents.removeIf { it.id == eventId }
        }

        if (!hasWritePermission() || context == null) {
            return false
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 重複・時間帯の重複する予定を検出する。
     */
    fun detectDuplicates(event: CalendarEvent): List<CalendarEvent> {
        if (event.endTime <= event.startTime) {
            return emptyList()
        }
        val existing = getEvents(event.startTime - 60000, event.endTime + 60000)
        return existing.filter { other ->
            other.id != event.id && (
                other.title.trim().equals(event.title.trim(), ignoreCase = true) ||
                (other.startTime < event.endTime && other.endTime > event.startTime)
            )
        }
    }
}
