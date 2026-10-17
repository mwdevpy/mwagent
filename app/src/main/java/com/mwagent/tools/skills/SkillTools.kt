package com.mwagent.tools.skills

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.skills.SkillLibrary
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import com.mwagent.tools.system.RunPythonTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SkillSaveTool(private val skills: SkillLibrary) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Saves a skill: a Python 3.13 script you write yourself for something no other tool does, kept on the phone and reusable in every chat. " +
            "The script reads its inputs from the `params` dict and prints its result. It can use the standard library, Telethon through `import tg`, " +
            "and the phone module when phone control from Python is on. Saving an existing name replaces it, so use it to fix a skill too.",
        parameters = objectSchema(required = listOf(NAME_ARG, DESCRIPTION, CODE)) {
            stringProperty(NAME_ARG, "Short snake_case name, for example telegram_unread_summary", maxLength = SkillLibrary.MAX_NAME_LENGTH)
            stringProperty(DESCRIPTION, "One line: what it does and which params it takes")
            stringProperty(CODE, "Python source of the skill")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override fun promptNote(settings: ToolSettings): String = buildString {
        append(
            "You can extend yourself with skills. When no tool covers what the user needs, write the missing logic as a Python skill, " +
                "save it with skill_save, run it with skill_run, and if it fails read the error, fix the code and save it again. " +
                "Do not tell the user something is impossible before trying a skill. Reuse a saved skill instead of writing it again.",
        )
        val saved = skills.list()
        if (saved.isNotEmpty()) {
            append("\nSaved skills (run with skill_run):")
            saved.take(MAX_LISTED).forEach { append("\n- ").append(it.name).append(": ").append(it.description) }
        }
    }

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val name = arguments.requireString(NAME_ARG)
        if (!SkillLibrary.isValidName(name)) throw ToolArgumentException("Use a snake_case name that starts with a letter")
        val existed = skills.find(name) != null
        val skill = skills.save(name, arguments.requireString(DESCRIPTION), arguments.requireString(CODE))
        return ToolOutcome("${if (existed) "Updated" else "Saved"} skill ${skill.name}. Run it with skill_run.")
    }

    companion object {
        const val NAME = "skill_save"
        private const val MAX_LISTED = 40
    }
}

class SkillRunTool(private val skills: SkillLibrary, private val python: RunPythonTool) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Runs a saved skill and returns what it printed. Pass its inputs as a JSON object in params.",
        parameters = objectSchema(required = listOf(NAME_ARG)) {
            stringProperty(NAME_ARG, "Skill name")
            stringProperty(PARAMS, "JSON object with the skill's inputs, for example {\"chat\": \"Mom\", \"limit\": 10}")
            integerProperty(TIMEOUT, "How long it may run in seconds, default 60", 1, MAX_TIMEOUT_S)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override val timeoutMs: Long get() = python.timeoutMs

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val name = arguments.requireString(NAME_ARG)
        val skill = skills.find(name) ?: return ToolOutcome.error("No skill called $name. Saved: ${skills.list().joinToString { it.name }.ifEmpty { "none" }}")
        val params = arguments.optionalString(PARAMS) ?: "{}"
        if (runCatching { Json.parseToJsonElement(params) as JsonObject }.isFailure) {
            throw ToolArgumentException("params must be a JSON object")
        }
        val code = launcher(skill.file.absolutePath, params)
        val run = buildJsonObject {
            put("code", code)
            put("timeout_seconds", arguments.optionalInt(TIMEOUT) ?: DEFAULT_TIMEOUT_S)
        }
        return python.execute(run, settings, context)
    }

    companion object {
        const val NAME = "skill_run"
        private const val DEFAULT_TIMEOUT_S = 60
        private const val MAX_TIMEOUT_S = 300

        fun launcher(path: String, paramsJson: String): String = buildString {
            appendLine("import json")
            appendLine("params = json.loads(${JsonPrimitive(paramsJson)})")
            appendLine("__skill_file__ = ${JsonPrimitive(path)}")
            append("exec(compile(open(__skill_file__, encoding=\"utf-8\").read(), __skill_file__, \"exec\"))")
        }
    }
}

class SkillListTool(private val skills: SkillLibrary) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists saved skills, or with name returns the source of one so you can read or improve it.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty(NAME_ARG, "Skill to show the source of")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val name = arguments.optionalString(NAME_ARG)
        if (name != null) {
            val skill = skills.find(name) ?: return ToolOutcome.error("No skill called $name")
            return ToolOutcome(skill.file.readText())
        }
        val saved = skills.list()
        if (saved.isEmpty()) return ToolOutcome("No skills saved yet.")
        return ToolOutcome(saved.joinToString("\n") { "${it.name}: ${it.description}" })
    }

    companion object {
        const val NAME = "skill_list"
    }
}

class SkillDeleteTool(private val skills: SkillLibrary) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Deletes a saved skill.",
        parameters = objectSchema(required = listOf(NAME_ARG)) {
            stringProperty(NAME_ARG, "Skill name")
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val name = arguments.requireString(NAME_ARG)
        return if (skills.delete(name)) ToolOutcome("Deleted skill $name.") else ToolOutcome.error("No skill called $name")
    }

    companion object {
        const val NAME = "skill_delete"
    }
}

private const val NAME_ARG = "name"
private const val DESCRIPTION = "description"
private const val CODE = "code"
private const val PARAMS = "params"
private const val TIMEOUT = "timeout_seconds"
