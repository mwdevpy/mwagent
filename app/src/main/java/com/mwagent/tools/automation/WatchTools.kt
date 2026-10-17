package com.mwagent.tools.automation

import com.mwagent.data.automation.WatchRepository
import com.mwagent.data.db.WatchEntity
import com.mwagent.data.db.WatchSource
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.enumProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

private fun WatchEntity.describe(): String {
    val who = sender ?: if (source == WatchSource.TELEGRAM) "any private chat" else "any app"
    val word = keyword?.let { ", containing \"$it\"" }.orEmpty()
    val state = if (paused) ", paused" else ""
    return "[$id] \"$title\", ${source.name.lowercase()} from $who$word$state: $instruction"
}

class AddWatchTool(
    private val watches: WatchRepository,
    private val telegramLinked: suspend () -> Boolean,
    private val notificationsAllowed: () -> Boolean,
) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Sets up a watch that wakes you up on its own when something arrives: a new incoming Telegram message on " +
            "the user's linked account, or a new notification from any app on the phone. On each match you get the event " +
            "with the instruction and can act with your tools; the user gets your reply as a notification. Use it for requests " +
            "like \"tell me when mom writes\", \"summarize messages from the work chat\" or \"if the bank notifies about a " +
            "payment over 5000, warn me\". \"from\" narrows it to a Telegram chat or person (name, @username or id) or to an " +
            "app (its name or package); leave it empty for any private Telegram chat or any app. Write the instruction as a " +
            "full rule for yourself. By default results go to a chat of the watch's own; set \"here\" to post them in this chat.",
        parameters = objectSchema(required = listOf("title", "source", "instruction")) {
            stringProperty("title", "Short name shown in the notification, in the user's language")
            enumProperty("source", "Where to watch", listOf(TELEGRAM, NOTIFICATIONS))
            stringProperty("from", "Telegram chat or person, or app name or package")
            stringProperty("keyword", "Only react when the text contains this")
            stringProperty("instruction", "What to do with each matching event, up to ${WatchRepository.MAX_INSTRUCTION} characters")
            booleanProperty("here", "Post results in this chat instead of the watch's own chat")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val source = when (arguments.requireString("source").lowercase()) {
            TELEGRAM -> WatchSource.TELEGRAM
            NOTIFICATIONS -> WatchSource.NOTIFICATIONS
            else -> throw ToolArgumentException("source must be $TELEGRAM or $NOTIFICATIONS")
        }
        val chatId = if (arguments.optionalBoolean("here") == true) context.chatId else ""
        val watch = try {
            watches.create(
                arguments.requireString("title"),
                source,
                arguments.optionalString("from"),
                arguments.optionalString("keyword"),
                arguments.requireString("instruction"),
                chatId,
            )
        } catch (e: IllegalArgumentException) {
            return ToolOutcome.error(e.message.orEmpty())
        }
        val notes = buildList {
            add("Watch added ${watch.describe()}")
            if (source == WatchSource.TELEGRAM && !telegramLinked()) add("No Telegram account is linked yet, it starts working once the user links one in Settings > Telegram.")
            if (source == WatchSource.NOTIFICATIONS && !notificationsAllowed()) add("Notification access is off, it starts working once the user allows it in Settings > Access and permissions.")
        }
        return ToolOutcome(notes.joinToString("\n"))
    }

    companion object {
        const val NAME = "add_watch"
        private const val TELEGRAM = "telegram"
        private const val NOTIFICATIONS = "notifications"
    }
}

class ListWatchesTool(private val watches: WatchRepository) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists active watches with their ids, sources and instructions.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val list = watches.list()
        if (list.isEmpty()) return ToolOutcome("No watches")
        return ToolOutcome(list.joinToString("\n") { it.describe() })
    }

    companion object {
        const val NAME = "list_watches"
    }
}

class RemoveWatchTool(private val watches: WatchRepository) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Removes a watch by its id from ${ListWatchesTool.NAME}.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Watch id")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val id = arguments.requireString("id")
        return if (watches.delete(id)) ToolOutcome("Watch $id removed") else ToolOutcome.error("No watch with id $id")
    }

    companion object {
        const val NAME = "remove_watch"
    }
}
