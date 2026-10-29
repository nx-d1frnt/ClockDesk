package com.nxd1frnt.clockdesk2.agenda

import java.util.Calendar
import java.util.TimeZone

/** A civil date stays on the same day when the device changes time zone. Months are zero based. */
data class AgendaDate(val year: Int, val month: Int, val day: Int) {
    fun toMillis(zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, 12, 0) // Noon avoids midnight transitions in historical time zones.
    }.timeInMillis

    fun shiftMonth(amount: Int): AgendaDate = fromMillis(Calendar.getInstance().apply {
        timeInMillis = toMillis()
        add(Calendar.MONTH, amount)
    }.timeInMillis)

    fun monthCells(firstDayOfWeek: Int): List<AgendaDate?> {
        val first = Calendar.getInstance().apply { timeInMillis = copy(day = 1).toMillis() }
        val offset = (first.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val length = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        return List(42) { index ->
            (index - offset + 1).takeIf { it in 1..length }?.let { copy(day = it) }
        }
    }

    companion object {
        fun fromMillis(value: Long, zone: TimeZone = TimeZone.getDefault()): AgendaDate =
            Calendar.getInstance(zone).run {
                timeInMillis = value
                AgendaDate(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
            }
    }
}
