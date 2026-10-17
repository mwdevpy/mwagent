package com.mwagent.tools.automation

import com.mwagent.automation.SubagentState
import com.mwagent.automation.Subagents
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class StartSubagentTool(private val subagents: () -> Subagents) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Starts a helper that works on a long task in the background in a chat of its own, with the same model and " +
            "tools, while you keep talking with the user: research on several sites, going through many Telegram chats, " +
            "preparing a long document. Start several for independent parts. The helper does not see this conversation, so " +
            "write the task with all details it needs. Get its answer with ${CheckSubagentTool.NAME}; the user also gets a " +
            "notification when it finishes.",
        parameters = objectSchema(required = listOf("title", "task")) {
            stringProperty("title", "Short name of the helper chat, in the user's language")
            stringProperty("task", "The full task with everything the helper needs to know")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val id = try {
            subagents().start(context.chatId, arguments.requireString("title"), arguments.requireString("task"))
        } catch (e: IllegalStateException) {
            return ToolOutcome.error(e.message.orEmpty())
        }
        return ToolOutcome("Helper started, id $id. Check on it with ${CheckSubagentTool.NAME}.")
    }

    companion object {
        const val NAME = "start_subagent"
    }
}

class CheckSubagentTool(private val subagents: () -> Subagents) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Tells whether a helper from ${StartSubagentTool.NAME} is still working and returns its final answer when it " +
            "is done. Give wait_seconds to wait for it to finish.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Helper id")
            integerProperty("wait_seconds", "How long to wait for it to finish, default 0", minimum = 0, maximum = MAX_WAIT_S)
        },
    )

    override val timeoutMs: Long = (MAX_WAIT_S + 15) * MS_PER_SECOND

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val id = arguments.requireString("id")
        val wait = (arguments.optionalInt("wait_seconds") ?: 0).coerceIn(0, MAX_WAIT_S) * MS_PER_SECOND
        return when (val state = subagents().state(id, wait)) {
            SubagentState.Running -> ToolOutcome("Still working")
            is SubagentState.Done -> ToolOutcome("Done. Its answer:\n\n${state.reply}")
            is SubagentState.Failed -> ToolOutcome.error("The helper failed: ${state.reason}")
            SubagentState.Unknown -> ToolOutcome.error("No helper with id $id")
        }
    }

    companion object {
        const val NAME = "check_subagent"
        private const val MAX_WAIT_S = 120
        private const val MS_PER_SECOND = 1_000L
    }
}
