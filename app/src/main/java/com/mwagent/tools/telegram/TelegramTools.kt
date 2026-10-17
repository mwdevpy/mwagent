package com.mwagent.tools.telegram

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.files.DeviceFiles
import com.mwagent.telegram.TelegramUserApi
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object TelegramToolReply {
    private val json = Json { ignoreUnknownKeys = true }

    fun toOutcome(raw: String): ToolOutcome {
        val error = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?.get("error")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        return if (error != null) ToolOutcome.error(error) else ToolOutcome(raw)
    }
}

private const val NOT_LINKED_HINT = "If it says no account is linked, tell the user to link one in Settings > Telegram."

class TelegramMeTool(private val telegram: TelegramUserApi) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "The user's own linked Telegram account: id, username, first and last name, phone, Premium status and bio. " +
            "Call it for any question about their Telegram account instead of saying you have no access. $NOT_LINKED_HINT",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome =
        TelegramToolReply.toOutcome(telegram.tool("me"))

    override fun promptNote(settings: ToolSettings): String =
        "The user's Telegram account can be linked in the app. Use the telegram_* tools to answer questions about it and to read or send messages; " +
            "never say you cannot access it before calling them. For anything they cannot do, write a run_python script with `import tg`."

    companion object {
        const val NAME = "telegram_me"
    }
}

class TelegramChatsTool(private val telegram: TelegramUserApi) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "The user's Telegram chats, newest first, with id, name and unread count. $NOT_LINKED_HINT",
        parameters = objectSchema(required = emptyList()) {
            integerProperty(LIMIT, "How many chats to return, default $DEFAULT_LIMIT", 1, MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val limit = (arguments.optionalInt(LIMIT) ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return TelegramToolReply.toOutcome(telegram.tool("dialogs", limit))
    }

    companion object {
        const val NAME = "telegram_chats"
    }
}

class TelegramMessagesTool(private val telegram: TelegramUserApi) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Recent messages of one Telegram chat, newest first. The chat is an @username, phone, numeric id, \"me\" for Saved Messages, " +
            "or the chat name as shown in the chat list. $NOT_LINKED_HINT",
        parameters = objectSchema(required = listOf(CHAT)) {
            stringProperty(CHAT, "Chat to read")
            integerProperty(LIMIT, "How many messages to return, default $DEFAULT_LIMIT", 1, MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val chat = arguments.requireString(CHAT)
        val limit = (arguments.optionalInt(LIMIT) ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return TelegramToolReply.toOutcome(telegram.tool("messages", chat, limit))
    }

    companion object {
        const val NAME = "telegram_messages"
    }
}

class TelegramSearchTool(private val telegram: TelegramUserApi) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Searches the user's Telegram messages for a word or phrase, newest first: in one chat, or in all chats when chat is left out. " +
            "Each result has the chat name, chat_id and message id. $NOT_LINKED_HINT",
        parameters = objectSchema(required = listOf(QUERY)) {
            stringProperty(QUERY, "Text to look for")
            stringProperty(CHAT, "Chat to search in, all chats when left out")
            integerProperty(LIMIT, "How many messages to return, default $DEFAULT_LIMIT", 1, MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val query = arguments.requireString(QUERY)
        val limit = (arguments.optionalInt(LIMIT) ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return TelegramToolReply.toOutcome(telegram.tool("search", query, arguments.optionalString(CHAT), limit))
    }

    companion object {
        const val NAME = "telegram_search"
    }
}

class TelegramSendTool(private val telegram: TelegramUserApi, private val files: DeviceFiles) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Sends a message from the user's own Telegram account: text, a file from the phone with the text as its caption, or both. " +
            "The chat is an @username, phone, numeric id, \"me\" for Saved Messages, or the chat name as shown in the chat list. " +
            "Pass reply_to with a message id to answer that message. Only send what the user asked for. $NOT_LINKED_HINT",
        parameters = objectSchema(required = listOf(CHAT)) {
            stringProperty(CHAT, "Chat to send to")
            stringProperty(TEXT, "Message text, or the caption when a file is sent")
            stringProperty(FILE, "Path of a file on the phone to send, relative paths resolve in the workspace")
            integerProperty(REPLY_TO, "Id of the message to reply to", 1, Int.MAX_VALUE)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val chat = arguments.requireString(CHAT)
        val text = arguments.optionalString(TEXT).orEmpty()
        val path = arguments.optionalString(FILE)?.trim()?.takeIf(String::isNotEmpty)
        val replyTo = arguments.optionalInt(REPLY_TO)
        if (path == null) {
            if (text.isBlank()) throw ToolArgumentException("Give the text to send, a file, or both")
            return TelegramToolReply.toOutcome(telegram.tool("send", chat, text, replyTo))
        }
        val reply = files.withLocalCopy(files.resolve(path), settings.access.files, MAX_FILE_BYTES) { local ->
            telegram.tool("send_file", chat, local.absolutePath, text, replyTo)
        }
        return TelegramToolReply.toOutcome(reply)
    }

    companion object {
        const val NAME = "telegram_send"
        private const val MAX_FILE_BYTES = 2_000L * 1024 * 1024
    }
}

private const val CHAT = "chat"
private const val TEXT = "text"
private const val QUERY = "query"
private const val FILE = "file"
private const val REPLY_TO = "reply_to"
private const val LIMIT = "limit"
private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 100
