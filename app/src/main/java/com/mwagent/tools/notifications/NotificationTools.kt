package com.mwagent.tools.notifications

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.notifications.NotificationLog
import com.mwagent.system.notifications.PhoneNotification
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.reminders.ReminderClock
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class ReadNotificationsTool(
    private val log: NotificationLog,
    private val allowed: () -> Boolean,
    private val clock: ReminderClock = ReminderClock(),
) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads notifications the phone received from other apps, newest first: messengers, SMS, mail, banks, " +
            "deliveries. Use it for \"what did I miss\", to find a login code or to sum up the last hour. It sees notifications " +
            "that arrived since notification access was turned on, plus the ones still in the shade.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("app", "Only from this app, by name or package")
            stringProperty("query", "Only notifications whose title or text contains this")
            integerProperty("minutes", "Only from the last this many minutes", minimum = 1, maximum = MAX_MINUTES)
            integerProperty("limit", "How many to return, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!allowed()) {
            return ToolOutcome.error("Notification access is off. Ask the user to allow it in Settings > Access and permissions > Notification access.")
        }
        val app = arguments.optionalString("app")
        val query = arguments.optionalString("query")
        val since = arguments.optionalInt("minutes")?.let { clock.now() - it * MS_PER_MINUTE } ?: 0L
        val limit = (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val found = log.recent()
            .filter { it.postedAt >= since }
            .filter { app == null || it.app.contains(app, ignoreCase = true) || it.pkg.contains(app, ignoreCase = true) }
            .filter { query == null || it.title.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true) }
            .take(limit)
        if (found.isEmpty()) return ToolOutcome("No matching notifications")
        return ToolOutcome(found.joinToString("\n\n") { it.describe() })
    }

    private fun PhoneNotification.describe(): String =
        "${clock.format(postedAt)} · $app\n" + listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")

    companion object {
        const val NAME = "read_notifications"
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
        private const val MAX_MINUTES = 7 * 24 * 60
        private const val MS_PER_MINUTE = 60_000L
    }
}
