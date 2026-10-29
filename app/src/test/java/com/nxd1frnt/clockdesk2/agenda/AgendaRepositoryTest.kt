package com.nxd1frnt.clockdesk2.agenda

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.TimeZone

class AgendaRepositoryTest {
    private fun instant(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time

    private fun event(start: String, end: String, allDay: Boolean = false) = AgendaEvent(
        1L, "Meeting", instant(start), instant(end), allDay, "", "")

    @Test fun `finished events disappear but ongoing overnight events remain`() {
        val zone = TimeZone.getTimeZone("UTC")
        val now = instant("2026-10-29 10:00")
        assertFalse(AgendaRepository.isRelevant(event("2026-10-29 09:00", "2026-10-29 10:00"), now, zone))
        assertTrue(AgendaRepository.isRelevant(event("2026-10-28 23:00", "2026-10-29 11:00"), now, zone))
        assertTrue(AgendaRepository.isRelevant(event("2026-10-29 11:00", "2026-10-29 12:00"), now, zone))
        assertFalse(AgendaRepository.isRelevant(event("2026-10-30 00:00", "2026-10-30 01:00"), now, zone))
    }

    @Test fun `all day uses calendar date in positive and negative time zones`() {
        val today = event("2026-10-29 00:00", "2026-10-30 00:00", true)
        val yesterday = event("2026-10-28 00:00", "2026-10-29 00:00", true)
        val tomorrow = event("2026-10-30 00:00", "2026-10-31 00:00", true)
        for ((zone, now) in listOf("GMT+14:00" to "2026-10-28 11:00", "GMT-10:00" to "2026-10-30 09:00")) {
            val tz = TimeZone.getTimeZone(zone)
            assertTrue(zone, AgendaRepository.isRelevant(today, instant(now), tz))
            assertFalse(zone, AgendaRepository.isRelevant(yesterday, instant(now), tz))
            assertFalse(zone, AgendaRepository.isRelevant(tomorrow, instant(now), tz))
        }
    }

    @Test fun `multi day all day events have exclusive end date`() {
        val event = event("2026-10-28 00:00", "2026-10-30 00:00", true)
        val zone = TimeZone.getTimeZone("Europe/Kyiv")
        assertTrue(AgendaRepository.isRelevant(event, instant("2026-10-29 10:00"), zone))
        assertFalse(AgendaRepository.isRelevant(event, instant("2026-10-30 10:00"), zone))
    }

    @Test fun `day bounds follow daylight saving transitions`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val spring = AgendaRepository.dayBounds(instant("2026-03-08 12:00"), zone)
        val autumn = AgendaRepository.dayBounds(instant("2026-11-01 12:00"), zone)
        assertEquals(23 * 3_600_000L, spring.second - spring.first)
        assertEquals(25 * 3_600_000L, autumn.second - autumn.first)
    }

    @Test fun `instantaneous event is retained until its start`() {
        val event = event("2026-10-29 10:00", "2026-10-29 10:00")
        val zone = TimeZone.getTimeZone("UTC")
        assertTrue(AgendaRepository.isRelevant(event, instant("2026-10-29 09:59"), zone))
        assertFalse(AgendaRepository.isRelevant(event, instant("2026-10-29 10:01"), zone))
    }
}
