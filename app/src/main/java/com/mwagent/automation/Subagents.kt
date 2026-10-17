package com.mwagent.automation

import com.mwagent.conversation.ConversationRunner
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import com.mwagent.data.db.selection
import com.mwagent.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

sealed interface SubagentState {
    data object Running : SubagentState
    data class Done(val reply: String) : SubagentState
    data class Failed(val reason: String) : SubagentState
    data object Unknown : SubagentState
}

class Subagents(
    private val chats: ChatRepository,
    private val conversations: ConversationRunner,
    private val settings: SettingsRepository,
    private val runs: AutoRuns,
    private val scope: CoroutineScope,
) {
    private val active = ConcurrentHashMap.newKeySet<String>()

    suspend fun start(parentId: String, title: String, task: String): String {
        check(active.size < MAX_RUNNING) { "$MAX_RUNNING helpers are already running, wait for one to finish" }
        val current = settings.current()
        val parent = chats.chat(parentId)
        val selection = parent?.selection ?: current.activeSelection ?: error("No model is selected")
        val chat = chats.createChat(title, selection, current.agent(parent?.agentId).id)
        active += chat.id
        scope.launch {
            try {
                runs.run(title, chat.id, task, NOTE)
            } finally {
                active -= chat.id
            }
        }
        return chat.id
    }

    suspend fun state(chatId: String, waitMs: Long): SubagentState {
        if (chats.chat(chatId) == null) return SubagentState.Unknown
        withTimeoutOrNull(waitMs) {
            while (busy(chatId)) delay(POLL_MS)
        }
        if (busy(chatId)) return SubagentState.Running
        val reply = chats.history(chatId).lastOrNull { it.role == MessageRole.ASSISTANT } ?: return SubagentState.Unknown
        return when (reply.status) {
            MessageStatus.COMPLETE -> SubagentState.Done(reply.content.trim())
            MessageStatus.ERROR -> SubagentState.Failed(reply.errorText.orEmpty())
            MessageStatus.CANCELLED -> SubagentState.Failed("cancelled")
            MessageStatus.STREAMING -> SubagentState.Running
        }
    }

    private fun busy(chatId: String): Boolean = chatId in active || conversations.isRunning(chatId)

    private companion object {
        const val MAX_RUNNING = 3
        const val POLL_MS = 1_000L
        const val NOTE = "You are a helper started by the assistant in another chat to do this task in the background; nobody is " +
            "watching this chat. Work on your own with your tools and finish with a complete, self-contained answer, because only " +
            "your last reply is handed back. Do not ask questions, make reasonable assumptions and state them."
    }
}
