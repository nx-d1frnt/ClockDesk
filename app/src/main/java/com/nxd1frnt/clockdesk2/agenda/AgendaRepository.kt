package com.nxd1frnt.clockdesk2.agenda

import android.content.ContentResolver
import android.content.ContentUris
import android.os.CancellationSignal
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val calendarName: String
)

/** Calendar Instances expands recurring events and their exceptions for the requested day. */
class AgendaRepository(private val resolver: ContentResolver) {
    fun loadToday(now: Long, zone: TimeZone, cancellation: CancellationSignal): List<AgendaEvent> {
        return loadDay(now, now, zone, cancellation)
    }

    fun loadDay(day: Long, now: Long, zone: TimeZone, cancellation: CancellationSignal): List<AgendaEvent> {
        val (dayStart, dayEnd) = dayBounds(day, zone)
        // All-day dates are stored at UTC midnight, regardless of the device's zone.
        val utcStart = utcDate(dayStart, zone)
        val utcEnd = utcDate(dayEnd, zone)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, minOf(dayStart, utcStart))
            ContentUris.appendId(this, maxOf(dayEnd, utcEnd))
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?) AND " +
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ?)"
        val events = mutableListOf<AgendaEvent>()
        val cursor = resolver.query(uri, projection, selection, arrayOf(
            CalendarContract.Events.STATUS_CANCELED.toString(),
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()
        ), "${CalendarContract.Instances.BEGIN} ASC", cancellation)
            ?: error("Calendar provider unavailable")
        cursor.use {
            while (it.moveToNext()) {
                cancellation.throwIfCanceled()
                val event = AgendaEvent(it.getLong(0), it.getString(1).orEmpty(), it.getLong(2),
                    it.getLong(3), it.getInt(4) != 0, it.getString(5).orEmpty(), it.getString(6).orEmpty())
                if (isRelevantOnDay(event, day, now, zone)) events += event
            }
        }
        return events.sortedWith(compareBy<AgendaEvent> { !it.allDay }.thenBy { it.begin }.thenBy { it.title })
    }

    companion object {
        fun dayBounds(now: Long, zone: TimeZone): Pair<Long, Long> {
            val day = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = day.timeInMillis
            day.add(Calendar.DATE, 1)
            return start to day.timeInMillis
        }

        private fun utcDate(localMidnight: Long, zone: TimeZone): Long {
            val local = Calendar.getInstance(zone).apply { timeInMillis = localMidnight }
            return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DATE))
            }.timeInMillis
        }

        fun isRelevant(event: AgendaEvent, now: Long, zone: TimeZone): Boolean {
            return isRelevantOnDay(event, now, now, zone)
        }

        fun isRelevantOnDay(event: AgendaEvent, day: Long, now: Long, zone: TimeZone): Boolean {
            val (start, end) = dayBounds(day, zone)
            val cutoff = if (start == dayBounds(now, zone).first) now else start
            return if (event.allDay) {
                event.begin < utcDate(end, zone) && event.end > utcDate(start, zone)
            } else {
                event.begin < end && (event.end > cutoff || (event.begin == event.end && event.begin >= cutoff))
            }
        }
    }
}
