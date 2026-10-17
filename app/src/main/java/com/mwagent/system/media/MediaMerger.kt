package com.mwagent.system.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** Joins a video-only and an audio-only MP4 stream into one MP4 with Android's own muxer, without re-encoding. */
object MediaMerger {
    private const val BUFFER_BYTES = 4 * 1024 * 1024

    suspend fun merge(video: File, audio: File, output: File) = withContext(Dispatchers.IO) {
        val videoSource = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val audioSource = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val videoTrack = videoSource.selectFirst("video/") ?: throw IOException("${video.name} has no video track")
            val audioTrack = audioSource.selectFirst("audio/") ?: throw IOException("${audio.name} has no audio track")
            val videoOut = muxer.addTrack(videoSource.getTrackFormat(videoTrack))
            val audioOut = muxer.addTrack(audioSource.getTrackFormat(audioTrack))
            muxer.start()
            started = true
            val buffer = ByteBuffer.allocate(BUFFER_BYTES)
            val info = MediaCodec.BufferInfo()
            for ((source, track) in listOf(videoSource to videoOut, audioSource to audioOut)) {
                while (true) {
                    ensureActive()
                    val size = source.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, source.sampleTime, if (source.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                    source.advance()
                }
            }
            started = false
            muxer.stop()
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            videoSource.release()
            audioSource.release()
            runCatching { if (started) muxer.stop() }
            muxer.release()
        }
    }

    private fun MediaExtractor.selectFirst(mimePrefix: String): Int? =
        (0 until trackCount).firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(mimePrefix) }?.also(::selectTrack)
}
