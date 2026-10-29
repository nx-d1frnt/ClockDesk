package com.nxd1frnt.clockdesk2.agenda

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AgendaDateTest {
    @Test fun `month grid accounts for leap years and locale week start`() {
        val leap = AgendaDate(2024, Calendar.FEBRUARY, 1)
        val monday = leap.monthCells(Calendar.MONDAY)
        val sunday = leap.monthCells(Calendar.SUNDAY)
        assertEquals(42, monday.size)
        assertEquals(29, monday.count { it != null })
        assertEquals(3, monday.indexOfFirst { it != null })
        assertEquals(4, sunday.indexOfFirst { it != null })
        assertEquals(28, AgendaDate(2025, Calendar.FEBRUARY, 1).monthCells(Calendar.MONDAY).count { it != null })
    }

    @Test fun `month navigation clamps day and crosses year boundaries`() {
        assertEquals(AgendaDate(2024, Calendar.FEBRUARY, 29), AgendaDate(2024, Calendar.JANUARY, 31).shiftMonth(1))
        assertEquals(AgendaDate(2026, Calendar.DECEMBER, 15), AgendaDate(2027, Calendar.JANUARY, 15).shiftMonth(-1))
        assertEquals(AgendaDate(2027, Calendar.JANUARY, 31), AgendaDate(2026, Calendar.DECEMBER, 31).shiftMonth(1))
    }

    @Test fun `civil date is preserved in time zones on either side of UTC`() {
        val date = AgendaDate(2026, Calendar.OCTOBER, 29)
        for (name in listOf("GMT+14:00", "GMT-10:00", "America/New_York")) {
            val zone = TimeZone.getTimeZone(name)
            assertEquals(date, AgendaDate.fromMillis(date.toMillis(zone), zone))
        }
    }

    @Test fun `past and future dates show their complete schedule`() {
        val zone = TimeZone.getTimeZone("UTC")
        val now = AgendaDate(2026, Calendar.OCTOBER, 29).toMillis(zone)
        val yesterday = AgendaDate(2026, Calendar.OCTOBER, 28).toMillis(zone)
        val tomorrow = AgendaDate(2026, Calendar.OCTOBER, 30).toMillis(zone)
        val event = AgendaEvent(1, "Past meeting", yesterday, yesterday + 60_000, false, "", "")
        assertTrue(AgendaRepository.isRelevantOnDay(event, yesterday, now, zone))
        assertFalse(AgendaRepository.isRelevantOnDay(event, tomorrow, now, zone))
        val future = event.copy(begin = tomorrow, end = tomorrow + 60_000)
        assertTrue(AgendaRepository.isRelevantOnDay(future, tomorrow, now, zone))
    }
}
