package com.mwagent.conversation

import com.mwagent.data.db.MessageEntity
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecentHistoryTest {
    private fun message(id: String, role: MessageRole) =
        MessageEntity(id, "chat", role, id, null, MessageStatus.COMPLETE, null, id.length.toLong(), null, null, null)

    private val history = listOf(
        message("u1", MessageRole.USER),
        message("a1", MessageRole.ASSISTANT),
        message("u2", MessageRole.USER),
        message("a2", MessageRole.ASSISTANT),
        message("t2", MessageRole.TOOL),
        message("a2b", MessageRole.ASSISTANT),
        message("u3", MessageRole.USER),
        message("a3", MessageRole.ASSISTANT),
    )

    @Test
    fun `no limit keeps everything`() {
        assertSame(history, TurnAssembler.recent(history, null))
    }

    @Test
    fun `limit cuts at a user message so tool calls stay with their results`() {
        assertEquals(listOf("u2", "a2", "t2", "a2b", "u3", "a3"), TurnAssembler.recent(history, 2).map { it.id })
        assertEquals(listOf("u3", "a3"), TurnAssembler.recent(history, 1).map { it.id })
    }

    @Test
    fun `a limit above the number of turns keeps everything`() {
        assertEquals(history, TurnAssembler.recent(history, 10))
    }
}
