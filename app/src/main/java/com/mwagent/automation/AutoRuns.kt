package com.mwagent.automation

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mwagent.R
import com.mwagent.conversation.ConversationRunner
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.db.ChatEntity
import com.mwagent.data.db.MessageEntity
import com.mwagent.data.db.MessageRole
import com.mwagent.data.db.MessageStatus
import com.mwagent.data.settings.SettingsRepository
import com.mwagent.system.notifications.AppChannel
import com.mwagent.system.notifications.AppNotifications
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class AutoRuns(
    private val context: Context,
    private val chats: ChatRepository,
    private val conversations: ConversationRunner,
    private val settings: SettingsRepository,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun run(title: String, chatId: String, text: String, note: String): String? {
        val chat = chats.chat(chatId)?.takeIf { it.trashedAt == null } ?: newChat(title)
        if (chat == null) {
            notify(title, null, context.getString(R.string.automation_no_model))
            return null
        }
        locks.computeIfAbsent(chat.id) { Mutex() }.withLock {
            conversations.await(chat.id)
            conversations.send(chat.id, text, emptyList(), note)
            conversations.await(chat.id)
        }
        chats.history(chat.id).lastOrNull { it.role == MessageRole.ASSISTANT }?.summary()?.let { notify(title, chat.id, it) }
        return chat.id
    }

    private suspend fun newChat(title: String): ChatEntity? {
        val current = settings.current()
        val selection = current.activeSelection ?: return null
        return chats.createChat(title, selection, current.agent(null).id, CONTEXT_TURNS)
    }

    private fun MessageEntity.summary(): String? = when (status) {
        MessageStatus.ERROR -> errorText
        MessageStatus.COMPLETE -> content.trim().takeUnless { it.isEmpty() || it == QUIET }?.let(::plain)
        else -> null
    }

    @SuppressLint("MissingPermission")
    private fun notify(title: String, chatId: String?, text: String) {
        if (!AppNotifications.canPost(context)) return
        val tag = chatId ?: title
        val notification = NotificationCompat.Builder(context, AppChannel.AUTOMATIONS.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(AppNotifications.openChatIntent(context, chatId, tag.hashCode()))
            .build()
        NotificationManagerCompat.from(context).notify(tag, NOTIFICATION_ID, notification)
    }

    companion object {
        const val QUIET = "SKIP"
        private const val CONTEXT_TURNS = 10
        private const val NOTIFICATION_ID = 4_200
        private const val MAX_TEXT = 1_500
        private val MARKUP = Regex("""\*\*|__|`|^#{1,6}\s+""", RegexOption.MULTILINE)
        private val LINK = Regex("""\[([^\]]+)]\([^)]+\)""")

        fun plain(text: String): String = text.replace(LINK, "$1").replace(MARKUP, "").trim().take(MAX_TEXT)
    }
}
