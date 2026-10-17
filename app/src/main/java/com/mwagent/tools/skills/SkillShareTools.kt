package com.mwagent.tools.skills

import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.skills.SkillLibrary
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class SkillExportTool(private val skills: SkillLibrary, private val attachments: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Exports a saved skill as a .py file: it is saved to Downloads/MW AGENT and shown in the chat, so the user can " +
            "send it to someone. The other person imports it with ${SkillImportTool.NAME}.",
        parameters = objectSchema(required = listOf("name")) {
            stringProperty("name", "Skill name")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val name = arguments.requireString("name")
        val skill = skills.find(name) ?: return ToolOutcome.error("No skill called $name")
        val file = attachments.importLocal(skill.file, forModel = false)
        attachments.exportToDownloads(attachments.viewable(file))
        return ToolOutcome("Skill $name saved to Downloads/MW AGENT/${skill.file.name} and attached", attachments = listOf(file))
    }

    companion object {
        const val NAME = "skill_export"
    }
}

class SkillImportTool(private val skills: SkillLibrary, private val files: DeviceFiles) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Imports a skill from a .py file, for example one the user received and attached to the chat or saved in " +
            "Downloads. The file must start with a docstring line that describes the skill. Read the code before importing and " +
            "tell the user what it does, because it runs on their phone.",
        parameters = objectSchema(required = listOf("path")) {
            stringProperty("path", "Path to the .py file")
            stringProperty("name", "Skill name, by default the file name", maxLength = SkillLibrary.MAX_NAME_LENGTH)
        },
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val file = files.resolve(arguments.requireString("path"))
        val name = arguments.optionalString("name") ?: file.nameWithoutExtension.lowercase().replace(NON_NAME, "_").trim('_')
        if (!SkillLibrary.isValidName(name)) throw ToolArgumentException("Give a snake_case name that starts with a letter")
        val source = files.withLocalCopy(file, settings.access.files, MAX_BYTES) { it.readText() }
        val existed = skills.find(name) != null
        val skill = try {
            skills.import(name, source)
        } catch (e: IllegalArgumentException) {
            return ToolOutcome.error(e.message.orEmpty())
        }
        return ToolOutcome("${if (existed) "Replaced" else "Imported"} skill ${skill.name}: ${skill.description}")
    }

    companion object {
        const val NAME = "skill_import"
        private const val MAX_BYTES = 256L * 1024
        private val NON_NAME = Regex("[^a-z0-9_]+")
    }
}
