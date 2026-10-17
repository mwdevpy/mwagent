package com.mwagent.tools.media

import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.media.PhotoLibrary
import com.mwagent.system.media.PhotoQuery
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.reminders.ReminderClock
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

private const val NO_ACCESS = "Photo access is off. Ask the user to allow it in Settings > Access and permissions."

class FindPhotosTool(
    private val photos: PhotoLibrary,
    private val attachments: AttachmentStore,
    private val clock: ReminderClock = ReminderClock(),
) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Looks through the photos in the phone's gallery, newest first, and shows you small previews so you can see " +
            "what is on them: use it to find a receipt, a document, a screenshot or a photo of something. Narrow it by dates, " +
            "album (Camera, Screenshots, WhatsApp Images…) or file name, and page with offset if the right one is not there. " +
            "Open a found photo in full size with ${GetPhotoTool.NAME}.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("from", "First local date, like 2026-10-01")
            stringProperty("to", "Last local date, inclusive")
            stringProperty("album", "Album name or part of it")
            stringProperty("name", "File name or part of it")
            integerProperty("offset", "How many matching photos to skip", minimum = 0, maximum = MAX_OFFSET)
            integerProperty("limit", "How many previews to show, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!photos.canRead) return ToolOutcome.error(NO_ACCESS)
        val query = PhotoQuery(
            from = arguments.optionalString("from")?.let { day(it) },
            to = arguments.optionalString("to")?.let { day(it) + MS_PER_DAY },
            album = arguments.optionalString("album"),
            name = arguments.optionalString("name"),
        )
        val offset = (arguments.optionalInt("offset") ?: 0).coerceIn(0, MAX_OFFSET)
        val found = photos.find(query, offset, (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT))
        if (found.isEmpty()) return ToolOutcome("No matching photos")
        val previews = found.mapNotNull { photo -> runCatching { attachments.importBytes("${photo.id}.jpg", photos.thumbnail(photo, PREVIEW_SIDE), forModel = true) }.getOrNull() }
        val lines = found.mapIndexed { index, photo ->
            "${index + 1}. id ${photo.id} · ${clock.format(photo.takenAt)} · ${photo.album} · ${photo.name} · ${photo.width}x${photo.height}"
        }
        return ToolOutcome("Previews follow in this order:\n" + lines.joinToString("\n"), attachments = previews)
    }

    private fun day(raw: String): Long = try {
        LocalDate.parse(raw.trim().take(DATE_LENGTH)).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        throw ToolArgumentException("Dates look like 2026-10-01")
    }

    companion object {
        const val NAME = "find_photos"
        private const val DEFAULT_LIMIT = 8
        private const val MAX_LIMIT = 12
        private const val MAX_OFFSET = 5_000
        private const val PREVIEW_SIDE = 384
        private const val DATE_LENGTH = 10
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

class GetPhotoTool(private val photos: PhotoLibrary, private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Opens one gallery photo in full size by its id from ${FindPhotosTool.NAME}, so you can read small text on it, " +
            "and shows it in the chat where the user can open, save or share it.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Photo id")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        if (!photos.canRead) return ToolOutcome.error(NO_ACCESS)
        val id = arguments.requireString("id").toLongOrNull() ?: throw ToolArgumentException("id is a number from ${FindPhotosTool.NAME}")
        val photo = photos.get(id) ?: return ToolOutcome.error("No photo with id $id")
        val file = attachments.import(photo.uri)
        return ToolOutcome("Photo ${photo.name} attached", attachments = listOf(file))
    }

    companion object {
        const val NAME = "get_photo"
    }
}
