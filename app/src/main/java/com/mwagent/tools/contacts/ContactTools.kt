package com.mwagent.tools.contacts

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.contacts.CallType
import com.mwagent.system.contacts.PhoneContacts
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.enumProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.reminders.ReminderClock
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class FindContactsTool(private val contacts: PhoneContacts) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Finds people in the phone's contacts by name, part of a name or phone number and returns their numbers and " +
            "emails. Use it before dial_number, compose_message or a Telegram message when the user names a person.",
        parameters = objectSchema(required = listOf("query")) {
            stringProperty("query", "Name, part of it or a phone number")
            integerProperty("limit", "How many contacts to return, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!contacts.canReadContacts) return ToolOutcome.error(NO_ACCESS.format("Contacts"))
        val query = arguments.requireString("query")
        val found = contacts.find(query, (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT))
        if (found.isEmpty()) return ToolOutcome("No contacts match \"$query\"")
        return ToolOutcome(
            found.joinToString("\n\n") { contact ->
                listOf(contact.name, *contact.phones.toTypedArray(), *contact.emails.toTypedArray()).joinToString("\n")
            },
        )
    }

    companion object {
        const val NAME = "find_contacts"
        private const val DEFAULT_LIMIT = 5
        private const val MAX_LIMIT = 20
    }
}

class CallLogTool(private val contacts: PhoneContacts, private val clock: ReminderClock = ReminderClock()) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads the phone's call history, newest first: who called, missed calls, how long a call took. " +
            "Use it for questions like \"who called me yesterday\" or \"did Anna call\".",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("query", "Only calls with this name or number")
            enumProperty("type", "Only calls of this kind", CallType.entries.map { it.name.lowercase() })
            integerProperty("days", "How many days back, default $DEFAULT_DAYS", minimum = 1, maximum = MAX_DAYS)
            integerProperty("limit", "How many calls to return, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!contacts.canReadCalls) return ToolOutcome.error(NO_ACCESS.format("Call log"))
        val type = arguments.optionalString("type")?.let { raw ->
            CallType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: throw ToolArgumentException("Unknown call type \"$raw\"")
        }
        val days = (arguments.optionalInt("days") ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        val calls = contacts.calls(
            arguments.optionalString("query"),
            type,
            clock.now() - days * MS_PER_DAY,
            (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT),
        )
        if (calls.isEmpty()) return ToolOutcome("No matching calls in the last $days days")
        return ToolOutcome(
            calls.joinToString("\n") { call ->
                val who = call.name?.let { "$it, ${call.number}" } ?: call.number
                val length = if (call.seconds > 0) ", ${call.seconds / SECONDS_PER_MINUTE} min ${call.seconds % SECONDS_PER_MINUTE} s" else ""
                "${clock.format(call.at)} · ${call.type.name.lowercase()} · $who$length"
            },
        )
    }

    companion object {
        const val NAME = "call_log"
        private const val DEFAULT_DAYS = 7
        private const val MAX_DAYS = 90
        private const val DEFAULT_LIMIT = 30
        private const val MAX_LIMIT = 200
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
        private const val SECONDS_PER_MINUTE = 60
    }
}

private const val NO_ACCESS = "%s access is off. Ask the user to allow it in Settings > Access and permissions."
