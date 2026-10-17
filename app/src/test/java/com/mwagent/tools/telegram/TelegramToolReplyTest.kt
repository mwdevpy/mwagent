package com.mwagent.tools.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramToolReplyTest {
    @Test
    fun `a result is passed through as it is`() {
        val raw = """{"id":1,"username":"mark","premium":true}"""
        val outcome = TelegramToolReply.toOutcome(raw)
        assertFalse(outcome.isError)
        assertEquals(raw, outcome.content)
    }

    @Test
    fun `a list result is passed through as it is`() {
        val outcome = TelegramToolReply.toOutcome("""[{"id":1,"name":"Mom","unread":2}]""")
        assertFalse(outcome.isError)
    }

    @Test
    fun `an error from python becomes a tool error`() {
        val outcome = TelegramToolReply.toOutcome("""{"error":"RuntimeError: No Telegram account is linked."}""")
        assertTrue(outcome.isError)
        assertEquals("RuntimeError: No Telegram account is linked.", outcome.content)
    }
}
