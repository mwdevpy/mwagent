package com.mwagent.data.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class TaskScheduleTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `one-time task runs once and then stops`() {
        val anchor = at("2026-10-19T08:00")
        assertEquals(anchor, TaskSchedule.next(anchor, 0, null, at("2026-10-18T12:00"), zone))
        assertNull(TaskSchedule.next(anchor, 0, null, anchor, zone))
    }

    @Test
    fun `interval keeps its phase after missed runs`() {
        val anchor = at("2026-10-18T08:00")
        assertEquals(at("2026-10-18T11:00"), TaskSchedule.next(anchor, 0, 60, at("2026-10-18T10:30"), zone))
        assertEquals(at("2026-10-18T09:00"), TaskSchedule.next(anchor, 0, 60, anchor, zone))
    }

    @Test
    fun `weekly task moves to the next chosen weekday`() {
        val days = TaskSchedule.mask(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val sunday = at("2026-10-18T08:00")
        assertEquals(at("2026-10-19T08:00"), TaskSchedule.next(sunday, days, null, sunday - 1, zone))
        assertEquals(at("2026-10-23T08:00"), TaskSchedule.next(at("2026-10-19T08:00"), days, null, at("2026-10-19T08:00"), zone))
    }

    @Test
    fun `weekly task keeps the wall clock time across daylight saving changes`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val anchor = LocalDateTime.parse("2026-10-24T08:00").atZone(berlin).toInstant().toEpochMilli()
        val next = TaskSchedule.next(anchor, TaskSchedule.mask(DayOfWeek.entries), null, anchor, berlin)
        assertEquals(LocalDateTime.parse("2026-10-25T08:00").atZone(berlin).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `day masks round trip`() {
        val days = listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, TaskSchedule.days(TaskSchedule.mask(days)))
    }
}
