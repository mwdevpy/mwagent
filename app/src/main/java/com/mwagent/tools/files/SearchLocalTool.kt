package com.mwagent.tools.files

import com.mwagent.data.search.LocalSearch
import com.mwagent.data.search.SearchArea
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.enumProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject

class SearchLocalTool(private val search: LocalSearch) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Searches by meaning through the user's own data on the phone: text files in the app's folders (notes, " +
            "documents, saved results, uploads) and the messages of all chats. Words match in any form and order, and when " +
            "embeddings are set up in memory settings, passages with the same meaning match too. Use it for \"where did I write " +
            "about…\", \"find my notes on…\" or \"what did we discuss about…\". Each hit names its file or chat and message ids, " +
            "so you can open it with read_file or read_chat.",
        parameters = objectSchema(required = listOf("query")) {
            stringProperty("query", "What to look for, in the user's words")
            enumProperty("in", "Where to look, default all", listOf(FILES, CHATS, ALL))
            integerProperty("limit", "How many passages to return, default $DEFAULT_LIMIT", minimum = 1, maximum = MAX_LIMIT)
        },
    )

    override val timeoutMs: Long = TIMEOUT_MS

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val areas = when (arguments.optionalString("in")?.lowercase()) {
            FILES -> setOf(SearchArea.FILES)
            CHATS -> setOf(SearchArea.CHATS)
            else -> SearchArea.entries.toSet()
        }
        val result = search.search(arguments.requireString("query"), areas, (arguments.optionalInt("limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT))
        val notes = listOfNotNull(result.warning, if (result.semantic) null else "Matched by words only.")
        if (result.hits.isEmpty()) return ToolOutcome((listOf("Nothing found, try other words.") + notes).joinToString("\n"))
        val hits = result.hits.mapIndexed { index, hit ->
            "${index + 1}. ${hit.passage.source} · ${hit.passage.title}\n${hit.passage.text.take(SNIPPET_CHARS).trim()}"
        }
        return ToolOutcome((hits + notes).joinToString("\n\n"))
    }

    companion object {
        const val NAME = "search_local"
        private const val FILES = "files"
        private const val CHATS = "chats"
        private const val ALL = "all"
        private const val DEFAULT_LIMIT = 8
        private const val MAX_LIMIT = 30
        private const val SNIPPET_CHARS = 600
        private const val TIMEOUT_MS = 120_000L
    }
}
