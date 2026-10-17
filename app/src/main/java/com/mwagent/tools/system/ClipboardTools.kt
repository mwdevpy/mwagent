package com.mwagent.tools.system

import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.clipboard.DeviceClipboard
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class CopyToClipboardTool(private val clipboard: DeviceClipboard) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Copies text to the phone's clipboard so the user can paste it anywhere: a code, an address, a ready message, a command.",
        parameters = objectSchema(required = listOf(TEXT)) {
            stringProperty(TEXT, "Text to copy")
            booleanProperty(SENSITIVE, "True for passwords, codes and other secrets, so Android hides the preview")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val text = arguments.requireString(TEXT)
        clipboard.copy(text, arguments.optionalBoolean(SENSITIVE) ?: false)
        return ToolOutcome("Copied ${text.length} characters to the clipboard.")
    }

    companion object {
        const val NAME = "copy_to_clipboard"
    }
}

class ReadClipboardTool(private val clipboard: DeviceClipboard) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads the text on the phone's clipboard, for when the user says they copied something or asks about what they copied.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override val defaultPermission = ToolPermission.ASK

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val text = clipboard.read()
            ?: return ToolOutcome.error("The clipboard is empty, holds no text, or Android hid it because the app is not on screen")
        val shown = if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n…[clipboard truncated at $MAX_CHARS characters]" else text
        return ToolOutcome(shown)
    }

    companion object {
        const val NAME = "read_clipboard"
        private const val MAX_CHARS = 20_000
    }
}

private const val TEXT = "text"
private const val SENSITIVE = "sensitive"
