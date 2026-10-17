package com.mwagent.system.media

import com.mwagent.system.python.PythonRuntime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

@Serializable
data class DownloadReply(
    val title: String = "",
    val author: String = "",
    val site: String = "",
    val duration: Double? = null,
    val files: List<String> = emptyList(),
    val merge: MergeParts? = null,
    val error: String? = null,
)

@Serializable
data class MergeParts(val video: String, val audio: String, val output: String)

data class DownloadedMedia(val title: String, val author: String, val site: String, val files: List<File>)

class MediaDownloadException(message: String) : IllegalStateException(message)

/** Downloads by link with the bundled media_dl.py (TikTok directly, other sites through yt-dlp) into [folder]. */
class MediaDownloader(private val python: PythonRuntime, private val folder: File) {

    suspend fun download(url: String, audioOnly: Boolean, maxHeight: Int, maxMb: Int, timeoutSeconds: Long): DownloadedMedia {
        val code = launcher(url, folder.absolutePath, audioOnly, maxHeight, maxMb)
        val result = python.run(code, folder, timeoutSeconds)
        if (result.timedOut) throw MediaDownloadException("The download took longer than $timeoutSeconds s and was stopped")
        val reply = parse(result.output) ?: throw MediaDownloadException("The downloader failed: ${result.output.takeLast(MAX_ERROR_CHARS)}")
        reply.error?.let { throw MediaDownloadException(it) }
        val files = reply.merge?.let { listOf(join(it)) } ?: reply.files.map(::File)
        if (files.isEmpty()) throw MediaDownloadException("Nothing was downloaded")
        return DownloadedMedia(reply.title, reply.author, reply.site, files)
    }

    private suspend fun join(parts: MergeParts): File {
        val video = File(parts.video)
        val audio = File(parts.audio)
        val output = File(parts.output)
        MediaMerger.merge(video, audio, output)
        video.delete()
        audio.delete()
        return output
    }

    companion object {
        private const val MAX_ERROR_CHARS = 600
        private val json = Json { ignoreUnknownKeys = true }

        fun launcher(url: String, folder: String, audioOnly: Boolean, maxHeight: Int, maxMb: Int): String = buildString {
            appendLine("import media_dl")
            append("print(media_dl.download(${JsonPrimitive(url)}, ${JsonPrimitive(folder)}, ")
            append(if (audioOnly) "True" else "False")
            append(", $maxHeight, $maxMb))")
        }

        /** The last JSON line the script printed; yt-dlp may print warnings before it. */
        fun parse(output: String): DownloadReply? =
            output.lineSequence().map(String::trim).filter { it.startsWith("{") }.lastOrNull()
                ?.let { runCatching { json.decodeFromString(DownloadReply.serializer(), it) }.getOrNull() }
    }
}
