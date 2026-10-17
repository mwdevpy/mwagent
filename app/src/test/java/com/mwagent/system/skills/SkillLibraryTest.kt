package com.mwagent.system.skills

import com.mwagent.tools.skills.SkillRunTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillLibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val library by lazy { SkillLibrary(folder.newFolder("skills")) }

    @Test
    fun `a saved skill is listed with its description`() {
        library.save("unread_summary", "Counts unread Telegram chats", "print(1)")
        val skills = library.list()
        assertEquals(listOf("unread_summary"), skills.map { it.name })
        assertEquals("Counts unread Telegram chats", skills.single().description)
        assertTrue(skills.single().file.readText().contains("print(1)"))
    }

    @Test
    fun `saving the same name replaces the skill`() {
        library.save("hello", "first", "print('a')")
        library.save("hello", "second", "print('b')")
        val skill = library.find("hello")!!
        assertEquals("second", skill.description)
        assertFalse(skill.file.readText().contains("print('a')"))
    }

    @Test
    fun `a docstring in the code is replaced by the description`() {
        library.save("doc", "new text", "\"\"\"old text\"\"\"\nprint(2)")
        val source = library.find("doc")!!.file.readText()
        assertTrue(source.startsWith("\"\"\"new text\"\"\""))
        assertFalse(source.contains("old text"))
        assertTrue(source.contains("print(2)"))
    }

    @Test
    fun `only snake case names are allowed`() {
        assertTrue(SkillLibrary.isValidName("ok_name2"))
        assertFalse(SkillLibrary.isValidName("Bad"))
        assertFalse(SkillLibrary.isValidName("../escape"))
        assertFalse(SkillLibrary.isValidName("2start"))
        assertNull(library.find("../escape"))
    }

    @Test
    fun `deleting removes the skill`() {
        library.save("gone", "x", "pass")
        assertTrue(library.delete("gone"))
        assertTrue(library.list().isEmpty())
        assertFalse(library.delete("gone"))
    }

    @Test
    fun `the launcher passes params as json and runs the skill file`() {
        val code = SkillRunTool.launcher("/data/skills/a.py", """{"chat":"Mom \"Ann\""}""")
        assertTrue(code.lines().any { it.startsWith("params = json.loads(\"") })
        assertTrue(code.contains("__skill_file__ = \"/data/skills/a.py\""))
        assertTrue(code.trimEnd().endsWith("\"exec\"))"))
    }
}
