package com.mwagent.telegram

import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.data.telegram.TelegramStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLoginTest {
    private val keys = TelegramAccount(apiId = 12345, apiHash = "abc")

    @Test
    fun `without api keys the first step asks for them`() {
        assertEquals(TelegramStep.CREDENTIALS, TelegramAccount().step())
        assertEquals(TelegramStep.CREDENTIALS, TelegramAccount(apiId = 1, apiHash = " ").step())
    }

    @Test
    fun `with keys and nothing pending the phone is asked for`() {
        assertEquals(TelegramStep.PHONE, keys.step())
    }

    @Test
    fun `after a code is sent the code is asked for`() {
        assertEquals(TelegramStep.CODE, keys.copy(phone = "+79991234567", pendingCodeHash = "h").step())
    }

    @Test
    fun `two-step verification switches to the password`() {
        val account = keys.copy(pendingCodeHash = "h", needsPassword = true)
        assertEquals(TelegramStep.PASSWORD, account.step())
    }

    @Test
    fun `a linked account shows the linked state even with leftovers`() {
        val account = keys.copy(userId = 7, username = "mark", pendingCodeHash = "h")
        assertEquals(TelegramStep.LINKED, account.step())
        assertTrue(account.linked)
    }

    @Test
    fun `signing out keeps the api keys and drops the account`() {
        val linked = keys.copy(phone = "+7", session = "s", userId = 7, username = "mark", firstName = "M")
        val signedOut = linked.signedOut()
        assertEquals(keys.apiId, signedOut.apiId)
        assertEquals(keys.apiHash, signedOut.apiHash)
        assertFalse(signedOut.linked)
        assertEquals("", signedOut.session)
        assertNull(signedOut.pendingCodeHash)
    }

    @Test
    fun `a sign in reply is parsed from the python json`() {
        val reply = TelegramReply.parse(
            """{"session":"abc","user_id":42,"username":"mark","first_name":"Mark"}""",
        )
        assertEquals("abc", reply.session)
        assertEquals(42L, reply.userId)
        assertEquals("mark", reply.username)
        assertNull(reply.error)
    }

    @Test
    fun `a code reply is parsed with the phone code hash`() {
        val reply = TelegramReply.parse("""{"session":"s","phone_code_hash":"hash"}""")
        assertEquals("hash", reply.phoneCodeHash)
        assertFalse(reply.needPassword)
    }

    @Test
    fun `an error reply carries Telegram's error name`() {
        val reply = TelegramReply.parse("""{"error":"PhoneCodeInvalidError"}""")
        assertEquals(TelegramErrors.CODE_INVALID, reply.error)
    }

    @Test
    fun `a flood wait reply carries the seconds`() {
        val reply = TelegramReply.parse("""{"error":"FloodWaitError","seconds":120}""")
        assertEquals(TelegramErrors.FLOOD_WAIT, reply.error)
        assertEquals(120, reply.seconds)
    }
}
