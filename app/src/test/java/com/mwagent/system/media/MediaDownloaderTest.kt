package com.mwagent.system.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloaderTest {
    @Test
    fun `the reply is the last json line after yt-dlp warnings`() {
        val output = """
            WARNING: [youtube] No supported JavaScript runtime could be found
            ERROR: something noisy
            {"title": "Me at the zoo", "site": "Youtube", "merge": {"video": "/w/a.f133.mp4", "audio": "/w/a.f140.m4a", "output": "/w/a.mp4"}, "files": []}
        """.trimIndent()
        val reply = MediaDownloader.parse(output)!!
        assertEquals("Me at the zoo", reply.title)
        assertEquals("/w/a.mp4", reply.merge!!.output)
        assertTrue(reply.files.isEmpty())
    }

    @Test
    fun `an error reply is read and garbage gives null`() {
        assertEquals("TooLarge", MediaDownloader.parse("""{"error": "TooLarge"}""")!!.error)
        assertNull(MediaDownloader.parse("Traceback (most recent call last):\n  boom"))
    }

    @Test
    fun `the launcher quotes the link so it cannot break out of the python string`() {
        val code = MediaDownloader.launcher("https://x.com/a\"); import os; os.system(\"rm -rf /", "/w/downloads", audioOnly = true, maxHeight = 720, maxMb = 100)
        val call = code.lines()[1]
        assertTrue(call.startsWith("print(media_dl.download(\"https://x.com/a\\\"); import os; os.system(\\\"rm -rf /\", \"/w/downloads\", True, 720, 100))"))
    }
}
