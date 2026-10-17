package com.mwagent.data.automation

import com.mwagent.automation.AutoRuns
import com.mwagent.data.db.WatchEntity
import com.mwagent.data.db.WatchSource
import com.mwagent.system.notifications.NotificationLog
import com.mwagent.system.notifications.PhoneNotification
import com.mwagent.telegram.TelegramMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMatchTest {
    private fun watch(source: WatchSource, sender: String? = null, keyword: String? = null) =
        WatchEntity("w1", "Test", source, sender, keyword, "tell me", "chat", 0L)

    private fun telegram(chat: String, username: String = "", text: String = "hi", direct: Boolean = true) =
        TelegramMessage(chatId = 42, chat = chat, sender = chat, username = username, text = text, direct = direct).toEvent()

    @Test
    fun `telegram watch without sender reacts to private chats only`() {
        val any = watch(WatchSource.TELEGRAM)
        assertTrue(any.matches(telegram("Мама")))
        assertFalse(any.matches(telegram("Work chat", direct = false)))
    }

    @Test
    fun `sender matches by name part, username or id, ignoring case and at sign`() {
        assertTrue(watch(WatchSource.TELEGRAM, "мама").matches(telegram("Мама ❤️")))
        assertTrue(watch(WatchSource.TELEGRAM, "@Boss").matches(telegram("Иван", username = "boss")))
        assertTrue(watch(WatchSource.TELEGRAM, "42").matches(telegram("Иван")))
        assertFalse(watch(WatchSource.TELEGRAM, "папа").matches(telegram("Мама")))
    }

    @Test
    fun `keyword and source both have to fit`() {
        val bank = watch(WatchSource.NOTIFICATIONS, "Sber", "списание")
        val event = PhoneNotification("k", "ru.sberbankmobile", "SberBank", "Покупка", "Списание 5 400 ₽", 0L).toEvent()
        assertTrue(bank.matches(event))
        assertFalse(bank.matches(event.copy(text = "Зачисление 100 ₽")))
        assertFalse(watch(WatchSource.TELEGRAM).matches(event))
    }

    @Test
    fun `log skips repeated updates of the same notification`() {
        val log = NotificationLog(capacity = 2)
        val posted = mutableListOf<String>()
        log.onPosted = { posted += it.text }
        val first = PhoneNotification("k", "app", "App", "Anna", "hello", 1L)
        log.add(first, fresh = true)
        log.add(first.copy(postedAt = 2L), fresh = true)
        log.add(first.copy(text = "are you there?", postedAt = 3L), fresh = true)
        log.add(first.copy(key = "other", text = "old", postedAt = 0L), fresh = false)
        assertEquals(listOf("hello", "are you there?"), posted)
        assertEquals(listOf("are you there?", "old"), log.recent().map { it.text })
    }

    @Test
    fun `notification text loses markdown`() {
        assertEquals("Rain at 18:00, see forecast", AutoRuns.plain("## **Rain** at `18:00`, see [forecast](https://x.y)"))
    }
}
