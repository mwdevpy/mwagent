package com.mwagent.tools.apps

import com.mwagent.system.device.QrCodes
import com.mwagent.tools.ToolArgumentException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EventTimeTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun millis(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun `a timed event lasts an hour by default`() {
        val time = EventTime.parse("2026-10-20T15:30", null, zone)
        assertFalse(time.allDay)
        assertEquals(millis("2026-10-20T15:30+03:00[Europe/Moscow]"), time.startMillis)
        assertEquals(millis("2026-10-20T16:30+03:00[Europe/Moscow]"), time.endMillis)
    }

    @Test
    fun `a date alone is an all day event through the end date`() {
        val time = EventTime.parse("2026-10-20", "2026-10-21", zone)
        assertTrue(time.allDay)
        assertEquals(millis("2026-10-20T00:00+03:00[Europe/Moscow]"), time.startMillis)
        assertEquals(millis("2026-10-22T00:00+03:00[Europe/Moscow]"), time.endMillis)
    }

    @Test
    fun `an explicit offset wins over the phone zone`() {
        val time = EventTime.parse("2026-10-20T10:00Z", "2026-10-20 15:00", zone)
        assertEquals(millis("2026-10-20T10:00Z"), time.startMillis)
        assertEquals(millis("2026-10-20T15:00+03:00[Europe/Moscow]"), time.endMillis)
    }

    @Test(expected = ToolArgumentException::class)
    fun `an end before the start is refused`() {
        EventTime.parse("2026-10-20T15:00", "2026-10-20T14:00", zone)
    }

    @Test(expected = ToolArgumentException::class)
    fun `unreadable times are refused`() {
        EventTime.parse("tomorrow at five", null, zone)
    }

    @Test
    fun `a qr code holds cyrillic text`() {
        val matrix = QrCodes.matrix("WIFI:T:WPA;S:Дом;P:пароль123;;")
        assertTrue(matrix.width >= 700)
        val dark = (0 until matrix.height).sumOf { y -> (0 until matrix.width).count { x -> matrix[x, y] } }
        assertTrue(dark in 1 until matrix.width * matrix.height)
    }
}
