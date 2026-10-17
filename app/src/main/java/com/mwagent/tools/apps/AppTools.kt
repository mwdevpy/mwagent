package com.mwagent.tools.apps

import android.net.Uri
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.intents.IntentResult
import com.mwagent.system.intents.PhoneIntents
import com.mwagent.system.intents.TravelMode
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.enumProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalArray
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringArrayProperty
import com.mwagent.tools.stringProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private fun IntentResult.toOutcome(done: String): ToolOutcome = when (this) {
    IntentResult.OPENED -> ToolOutcome(done)
    IntentResult.APP_HIDDEN -> ToolOutcome.error("The app is in the background, Android only lets it open other apps while the user has it on screen")
    IntentResult.NO_APP -> ToolOutcome.error("No app on this phone can do this")
    IntentResult.BLOCKED -> ToolOutcome.error("The other app refused the request")
}

private const val USER_FINISHES = "The user finishes it there; tell them it is ready for them to confirm."

class DialTool(private val intents: PhoneIntents) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens the phone's dialer with a number filled in, so the user only taps call. It does not place the call itself.",
        parameters = objectSchema(required = listOf(NUMBER)) {
            stringProperty(NUMBER, "Phone number, for example +7 999 123-45-67 or *100#")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val number = arguments.requireString(NUMBER).trim()
        return withContext(Dispatchers.Main) { intents.dial(number) }.toOutcome("The dialer is open with $number. $USER_FINISHES")
    }

    companion object {
        const val NAME = "dial_number"
        private const val NUMBER = "number"
    }
}

class ComposeMessageTool(private val intents: PhoneIntents) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a ready SMS or email in the phone's messaging or mail app with the recipient and text filled in. " +
            "The user reviews it and taps send; nothing is sent by this tool.",
        parameters = objectSchema(required = listOf(KIND, TO, TEXT)) {
            enumProperty(KIND, "sms or email", listOf(SMS, EMAIL))
            stringProperty(TO, "Phone number for sms, email address for email (several separated by commas)")
            stringProperty(TEXT, "Message text")
            stringProperty(SUBJECT, "Email subject")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val to = arguments.requireString(TO).trim()
        val text = arguments.requireString(TEXT)
        val result = when (arguments.requireString(KIND)) {
            SMS -> withContext(Dispatchers.Main) { intents.sms(to, text) }
            EMAIL -> withContext(Dispatchers.Main) { intents.email(to, arguments.optionalString(SUBJECT).orEmpty(), text) }
            else -> throw ToolArgumentException("'$KIND' is $SMS or $EMAIL")
        }
        return result.toOutcome("The message to $to is ready in the app. $USER_FINISHES")
    }

    companion object {
        const val NAME = "compose_message"
        private const val KIND = "kind"
        private const val TO = "to"
        private const val TEXT = "text"
        private const val SUBJECT = "subject"
        private const val SMS = "sms"
        private const val EMAIL = "email"
    }
}

class NavigateTool(private val intents: PhoneIntents) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a route to a place in the phone's maps app, starting from where the user is now.",
        parameters = objectSchema(required = listOf(DESTINATION)) {
            stringProperty(DESTINATION, "Address, place name or coordinates")
            enumProperty(MODE, "How to get there, default driving", TravelMode.entries.map { it.value })
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val destination = arguments.requireString(DESTINATION).trim()
        val mode = arguments.optionalString(MODE)?.let { value -> TravelMode.entries.firstOrNull { it.value == value } } ?: TravelMode.DRIVING
        return withContext(Dispatchers.Main) { intents.navigate(destination, mode) }.toOutcome("The route to $destination is open in maps.")
    }

    companion object {
        const val NAME = "navigate_to"
        private const val DESTINATION = "destination"
        private const val MODE = "mode"
    }
}

class OpenLinkTool(private val intents: PhoneIntents) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a web link in the user's browser, or in the app that handles it (YouTube, maps, a store page).",
        parameters = objectSchema(required = listOf(URL)) {
            stringProperty(URL, "Full http(s) link")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val url = arguments.requireString(URL).trim()
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrEmpty()) throw ToolArgumentException("Only http(s) links can be opened")
        return withContext(Dispatchers.Main) { intents.openLink(uri) }.toOutcome("Opened $url.")
    }

    companion object {
        const val NAME = "open_link"
        private const val URL = "url"
    }
}

class ShareTool(private val intents: PhoneIntents, private val files: DeviceFiles, private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens the Android share sheet with text and/or files, so the user picks where to send them: a messenger, mail, Drive, Bluetooth and so on.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty(TEXT, "Text to share")
            stringArrayProperty(PATHS, "Files on the phone to share, up to $MAX_FILES")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val text = arguments.optionalString(TEXT)?.takeIf(String::isNotBlank)
        val paths = arguments.optionalArray(PATHS).mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        if (text == null && paths.isEmpty()) throw ToolArgumentException("Give text, files, or both")
        if (paths.size > MAX_FILES) throw ToolArgumentException("At most $MAX_FILES files")
        val shared = paths.map { path ->
            val file = files.resolve(path)
            val attachment = files.withLocalCopy(file, settings.access.files, MAX_BYTES) { attachments.importLinked(it) }
            attachments.viewable(attachment).copy(name = file.name)
        }
        return withContext(Dispatchers.Main) { intents.share(text, shared) }.toOutcome("The share sheet is open. The user picks where to send it.")
    }

    companion object {
        const val NAME = "share"
        private const val TEXT = "text"
        private const val PATHS = "paths"
        private const val MAX_FILES = 10
        private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    }
}

class AddCalendarEventTool(private val intents: PhoneIntents, private val zone: () -> ZoneId = ZoneId::systemDefault) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens a new event in the phone's calendar app with the title, time, place and notes filled in; the user taps save. " +
            "Times are local, ISO format: 2026-10-20T15:30 for a timed event, 2026-10-20 for an all-day event.",
        parameters = objectSchema(required = listOf(TITLE, START)) {
            stringProperty(TITLE, "Event title")
            stringProperty(START, "Start, for example 2026-10-20T15:30, or a date for all day")
            stringProperty(END, "End in the same format, default one hour after the start (or the same day when all day)")
            stringProperty(LOCATION, "Where it happens")
            stringProperty(NOTES, "Description")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val title = arguments.requireString(TITLE)
        val time = EventTime.parse(arguments.requireString(START), arguments.optionalString(END), zone())
        val result = withContext(Dispatchers.Main) {
            intents.calendarEvent(title, time.startMillis, time.endMillis, time.allDay, arguments.optionalString(LOCATION), arguments.optionalString(NOTES))
        }
        return result.toOutcome("The event \"$title\" is open in the calendar. $USER_FINISHES")
    }

    companion object {
        const val NAME = "add_calendar_event"
        private const val TITLE = "title"
        private const val START = "start"
        private const val END = "end"
        private const val LOCATION = "location"
        private const val NOTES = "notes"
    }
}

data class EventTime(val startMillis: Long, val endMillis: Long, val allDay: Boolean) {
    companion object {
        private const val DEFAULT_HOURS = 1L
        private const val MS_PER_HOUR = 3_600_000L

        fun parse(start: String, end: String?, zone: ZoneId): EventTime {
            val first = moment(start, zone)
            val allDay = first == null
            val startMillis = first ?: startOf(date(start), zone)
            val endMillis = when {
                end.isNullOrBlank() && allDay -> startOf(date(start).plusDays(1), zone)
                end.isNullOrBlank() -> startMillis + DEFAULT_HOURS * MS_PER_HOUR
                allDay -> startOf(date(end).plusDays(1), zone)
                else -> moment(end, zone) ?: throw ToolArgumentException("'end' must be a time like 2026-10-20T16:30 when 'start' has a time")
            }
            if (endMillis <= startMillis) throw ToolArgumentException("The event must end after it starts")
            return EventTime(startMillis, endMillis, allDay)
        }

        private fun moment(raw: String, zone: ZoneId): Long? {
            val text = raw.trim().replace(' ', 'T')
            if (!text.contains('T')) return null
            return try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (e: DateTimeParseException) {
                try {
                    LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
                } catch (e: DateTimeParseException) {
                    throw ToolArgumentException("Cannot read the time \"$raw\", use 2026-10-20T15:30")
                }
            }
        }

        private fun startOf(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

        private fun date(raw: String): LocalDate = try {
            LocalDate.parse(raw.trim())
        } catch (e: DateTimeParseException) {
            throw ToolArgumentException("Cannot read the date \"$raw\", use 2026-10-20 or 2026-10-20T15:30")
        }
    }
}
