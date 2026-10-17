package com.mwagent.telegram

import android.util.Log
import com.mwagent.data.automation.WatchEvent
import com.mwagent.data.db.WatchSource
import com.mwagent.data.telegram.TelegramAccount
import com.mwagent.system.python.PythonRuntime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TelegramMessage(
    val id: Long = 0,
    @SerialName("chat_id") val chatId: Long = 0,
    val chat: String = "",
    val sender: String = "",
    val username: String = "",
    val text: String = "",
    val media: String? = null,
    val direct: Boolean = false,
    val error: String? = null,
) {
    fun toEvent(): WatchEvent = WatchEvent(
        source = WatchSource.TELEGRAM,
        origin = listOf("Telegram", chat, sender.takeIf { it != chat }).filterNot { it.isNullOrBlank() }.joinToString(" · ") +
            username.takeIf { it.isNotEmpty() }?.let { " (@$it)" }.orEmpty(),
        names = listOf(chat, sender, username, chatId.toString()),
        text = text.ifEmpty { media?.let { "[$it]" }.orEmpty() },
        direct = direct,
    )
}

fun interface TelegramSink {
    fun accept(json: String)
}

class TelegramWatcher(private val python: PythonRuntime) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun start(account: TelegramAccount, onMessage: (TelegramMessage) -> Unit) {
        val sink = TelegramSink { raw ->
            val message = runCatching { json.decodeFromString(TelegramMessage.serializer(), raw) }.getOrNull() ?: return@TelegramSink
            if (message.error != null) Log.w(TAG, message.error) else onMessage(message)
        }
        python.call(MODULE, "start", account.apiId, account.apiHash, account.session, sink)
    }

    suspend fun stop() {
        python.call(MODULE, "stop")
    }

    private companion object {
        const val TAG = "TelegramWatcher"
        const val MODULE = "tg_watch"
    }
}
