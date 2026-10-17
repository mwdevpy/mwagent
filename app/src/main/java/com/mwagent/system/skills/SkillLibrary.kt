package com.mwagent.system.skills

import java.io.File

data class Skill(val name: String, val description: String, val file: File)

/** Python skills the agent writes for itself, one file per skill in [directory]. The first docstring line is the description. */
class SkillLibrary(val directory: File) {

    fun save(name: String, description: String, code: String): Skill {
        require(isValidName(name)) { "Skill names use lowercase letters, digits and underscores, start with a letter and have at most $MAX_NAME_LENGTH characters" }
        directory.mkdirs()
        val summary = description.trim().replace("\"\"\"", "'''").lineSequence().first()
        val body = code.trimEnd().let { if (it.trimStart().startsWith("\"\"\"")) stripDocstring(it) else it }
        val file = file(name)
        file.writeText("\"\"\"$summary\"\"\"\n\n${body.trim()}\n")
        return Skill(name, summary, file)
    }

    fun list(): List<Skill> =
        directory.listFiles { file -> file.isFile && file.extension == EXTENSION }
            .orEmpty()
            .map { Skill(it.nameWithoutExtension, describe(it.readText()), it) }
            .filter { isValidName(it.name) }
            .sortedBy { it.name }

    fun find(name: String): Skill? = file(name).takeIf { isValidName(name) && it.isFile }?.let { Skill(name, describe(it.readText()), it) }

    fun delete(name: String): Boolean = isValidName(name) && file(name).delete()

    fun install(bundled: Map<String, String>) {
        directory.mkdirs()
        val marker = File(directory, MARKER)
        val installed = marker.takeIf { it.isFile }?.readLines().orEmpty().toSet()
        val fresh = bundled.filterKeys { it !in installed && isValidName(it) }
        fresh.forEach { (name, source) -> file(name).takeUnless { it.exists() }?.writeText(source) }
        if (fresh.isNotEmpty()) marker.writeText((installed + fresh.keys).sorted().joinToString("\n"))
    }

    fun import(name: String, source: String): Skill {
        val description = describe(source)
        require(description.isNotEmpty()) { "The skill file has no description: start it with a docstring line" }
        return save(name, description, source)
    }

    private fun file(name: String) = File(directory, "$name.$EXTENSION")

    companion object {
        const val EXTENSION = "py"
        const val MAX_NAME_LENGTH = 40
        private const val MARKER = ".bundled"
        private val NAME = Regex("^[a-z][a-z0-9_]{0,${MAX_NAME_LENGTH - 1}}$")
        private val DOCSTRING = Regex("^\\s*(\"\"\"|''')(.*?)\\1", RegexOption.DOT_MATCHES_ALL)

        fun isValidName(name: String): Boolean = NAME.matches(name)

        fun describe(source: String): String =
            DOCSTRING.find(source)?.groupValues?.get(2)?.trim()?.lineSequence()?.firstOrNull()?.trim().orEmpty()

        private fun stripDocstring(source: String): String = DOCSTRING.find(source)?.let { source.substring(it.range.last + 1) } ?: source
    }
}
