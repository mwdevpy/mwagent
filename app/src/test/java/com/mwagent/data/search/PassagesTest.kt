package com.mwagent.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassagesTest {
    @Test
    fun `short text stays one passage and blank text gives none`() {
        assertEquals(listOf("hello"), Passages.split("  hello \n"))
        assertTrue(Passages.split("   ").isEmpty())
    }

    @Test
    fun `long text is cut into overlapping passages that cover all of it`() {
        val text = (1..500).joinToString(" ") { "word$it" }
        val parts = Passages.split(text, size = 300, step = 250)
        assertTrue(parts.all { it.length <= 300 })
        assertEquals(text.take(300), parts.first())
        assertTrue(parts.last().endsWith("word500"))
        assertTrue(parts.zipWithNext().all { (first, second) -> first.takeLast(50) == second.take(50) })
    }
}
