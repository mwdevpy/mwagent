package com.mwagent.tools.media

import com.mwagent.data.attachments.Attachment
import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.attachments.ByteSize
import com.mwagent.data.attachments.TextFormats
import com.mwagent.data.files.ViewableFile
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.media.MediaDownloader
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import java.io.IOException

class DownloadMediaTool(private val downloader: MediaDownloader, private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Downloads a video, its sound, or the pictures of a post by link: YouTube (videos and Shorts), TikTok (videos, photo posts, sounds) " +
            "and the many other sites yt-dlp supports, such as Instagram, X, VK, Reddit, Twitch clips and SoundCloud. " +
            "The file is saved on the phone, shown to the user in the chat, and by default also put in Downloads/MW AGENT. " +
            "Only download what the user asked for and has the right to save.",
        parameters = objectSchema(required = listOf(URL)) {
            stringProperty(URL, "Link to the video or post")
            booleanProperty(AUDIO_ONLY, "True for just the sound (music, podcasts)")
            integerProperty(MAX_HEIGHT, "Highest video quality in pixels, default $DEFAULT_HEIGHT", MIN_HEIGHT, MAX_HEIGHT_LIMIT)
            booleanProperty(SAVE, "Also put the file in the phone's Downloads/MW AGENT folder, default true")
        },
    )

    override val timeoutMs: Long = (TIMEOUT_S + MERGE_GRACE_S) * MS_PER_SECOND

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val url = arguments.requireString(URL).trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw ToolArgumentException("Give a full http(s) link")
        val media = downloader.download(
            url = url,
            audioOnly = arguments.optionalBoolean(AUDIO_ONLY) ?: false,
            maxHeight = (arguments.optionalInt(MAX_HEIGHT) ?: DEFAULT_HEIGHT).coerceIn(MIN_HEIGHT, MAX_HEIGHT_LIMIT),
            maxMb = MAX_MB,
            timeoutSeconds = TIMEOUT_S,
        )
        val save = arguments.optionalBoolean(SAVE) ?: true
        val shown = mutableListOf<Attachment>()
        val notes = mutableListOf<String>()
        for (file in media.files) {
            if (save) {
                try {
                    attachments.exportToDownloads(ViewableFile(file, file.name, TextFormats.guessMimeType(file.name)))
                } catch (e: IOException) {
                    notes += "Could not put ${file.name} in Downloads: ${e.message}"
                }
            }
            try {
                shown += attachments.importLinked(file)
            } catch (e: IOException) {
                notes += "${file.name} is too large to show in the chat: ${e.message}"
            }
        }
        val report = buildList {
            add("Downloaded from ${media.site.ifEmpty { "the link" }}: ${media.title.ifBlank { "untitled" }}${if (media.author.isNotEmpty()) " by ${media.author}" else ""}")
            media.files.forEach { add("- ${it.absolutePath} (${ByteSize.describe(it.length())})") }
            if (save) add("A copy is in Downloads/MW AGENT.")
            if (shown.isNotEmpty()) add("Shown to the user in the chat.")
            addAll(notes)
        }.joinToString("\n")
        return ToolOutcome(report, attachments = shown)
    }

    companion object {
        const val NAME = "download_media"
        private const val URL = "url"
        private const val AUDIO_ONLY = "audio_only"
        private const val MAX_HEIGHT = "max_height"
        private const val SAVE = "save_to_downloads"
        private const val DEFAULT_HEIGHT = 1080
        private const val MIN_HEIGHT = 144
        private const val MAX_HEIGHT_LIMIT = 2160
        private const val MAX_MB = 1500
        private const val TIMEOUT_S = 900L
        private const val MERGE_GRACE_S = 120L
        private const val MS_PER_SECOND = 1_000L
    }
}

class SaveToDownloadsTool(private val files: DeviceFiles, private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Copies a file the assistant made or downloaded into the phone's public Downloads/MW AGENT folder, " +
            "where the user finds it in their file manager and gallery. Use it when the user wants to keep or save a file.",
        parameters = objectSchema(required = listOf(PATH)) {
            stringProperty(PATH, "File to save, relative paths resolve in the workspace")
            stringProperty(NAME_ARG, "File name in Downloads, defaults to the current name")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val file = files.resolve(arguments.requireString(PATH))
        val name = arguments.optionalName() ?: file.name
        files.withLocalCopy(file, settings.access.files, MAX_BYTES) { local ->
            attachments.exportToDownloads(ViewableFile(local, name, TextFormats.guessMimeType(name)))
        }
        return ToolOutcome("Saved to Downloads/MW AGENT/$name.")
    }

    private fun JsonObject.optionalName(): String? =
        optionalString(NAME_ARG)
            ?.replace(UNSAFE_NAME, "_")?.trim(' ', '.')?.take(MAX_NAME)?.ifEmpty { null }

    companion object {
        const val NAME = "save_to_downloads"
        private const val PATH = "path"
        private const val NAME_ARG = "name"
        private const val MAX_NAME = 120
        private const val MAX_BYTES = 4L * 1024 * 1024 * 1024
        private val UNSAFE_NAME = Regex("[\\\\/:*?\"<>|\\r\\n\\t]")
    }
}
