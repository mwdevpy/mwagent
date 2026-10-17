package com.mwagent.data.search

import android.util.Log
import com.mwagent.data.attachments.TextFormats
import com.mwagent.data.chat.ChatRepository
import com.mwagent.data.memory.MemoryRanking
import com.mwagent.data.memory.VectorCodec
import com.mwagent.data.settings.MemorySettings
import com.mwagent.llm.EmbeddingClient
import com.mwagent.llm.EmbeddingPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

enum class SearchArea { FILES, CHATS }

data class Passage(val source: String, val title: String, val text: String, val at: Long)

data class LocalHit(val passage: Passage, val score: Float)

data class LocalResult(val hits: List<LocalHit>, val semantic: Boolean, val warning: String?)

object Passages {
    fun split(text: String, size: Int = CHUNK, step: Int = STEP): List<String> {
        val clean = text.trim()
        if (clean.length <= size) return listOf(clean).filter { it.isNotEmpty() }
        return (0 until clean.length step step).map { clean.substring(it, minOf(clean.length, it + size)) }.takeWhile { it.isNotBlank() }
    }

    private const val CHUNK = 1_200
    private const val STEP = 1_000
}

class LocalSearch(
    private val roots: () -> List<File>,
    private val chats: ChatRepository,
    private val embeddings: EmbeddingClient,
    private val settings: Flow<MemorySettings>,
    private val cache: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val vectors by lazy { load() }

    suspend fun search(query: String, areas: Set<SearchArea>, limit: Int): LocalResult = withContext(Dispatchers.IO) {
        val passages = buildList {
            if (SearchArea.FILES in areas) addAll(files())
            if (SearchArea.CHATS in areas) addAll(messages())
        }
        val lexical = passages.associateWith { MemoryRanking.lexical(query, it.title, it.text) }
        val config = settings.first()
        val plain = { MemoryRanking.rank(passages, limit, { lexical.getValue(it) }).map { LocalHit(it.item, it.score) } }
        if (!config.embeddingsReady || passages.isEmpty()) return@withContext LocalResult(plain(), semantic = false, warning = null)
        val pool = (passages.filter { lexical.getValue(it) > 0f }.sortedByDescending { lexical.getValue(it) } + passages.sortedByDescending { it.at })
            .distinct()
            .take(SEMANTIC_POOL)
        try {
            val known = embedded(pool, config)
            val target = embeddings.embed(listOf(query), EmbeddingPurpose.QUERY, config).single()
            val ranked = MemoryRanking.rank(pool, limit, { lexical.getValue(it) }, { passage -> known[passage.text]?.let { MemoryRanking.cosine(target, it) } })
            LocalResult(ranked.map { LocalHit(it.item, it.score) }, semantic = true, warning = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Semantic search failed, falling back to keywords", e)
            LocalResult(plain(), semantic = false, warning = "Semantic search is unavailable (${e.message ?: e.javaClass.simpleName}), matched by keywords instead.")
        }
    }

    private fun files(): List<Passage> = roots()
        .flatMap { root -> root.walkTopDown().maxDepth(MAX_DEPTH).onEnter { !it.name.startsWith(".") }.filter { it.isFile }.map { root to it } }
        .filter { (_, file) -> file.length() in 1..MAX_FILE_BYTES && TextFormats.isText(TextFormats.guessMimeType(file.name), file.name) }
        .sortedByDescending { (_, file) -> file.lastModified() }
        .take(MAX_FILES)
        .flatMap { (root, file) ->
            val path = root.name + "/" + file.relativeTo(root).invariantSeparatorsPath
            val text = runCatching { file.readText() }.getOrDefault("")
            Passages.split(text).mapIndexed { index, part -> Passage("file $path${if (index > 0) " part ${index + 1}" else ""}", path, part, file.lastModified()) }
        }

    private suspend fun messages(): List<Passage> = chats.recentMessages(MAX_MESSAGES).map { hit ->
        Passage("chat ${hit.chatId} message ${hit.messageId}", hit.chatTitle, hit.content.take(MAX_MESSAGE_CHARS), hit.createdAt)
    }

    private suspend fun embedded(pool: List<Passage>, config: MemorySettings): Map<String, FloatArray> {
        val keys = pool.associate { it.text to key(config.embeddingSignature, it.text) }
        val missing = pool.map { it.text }.distinct().filter { vectors[keys.getValue(it)] == null }
        missing.chunked(BATCH).forEach { batch ->
            embeddings.embed(batch, EmbeddingPurpose.DOCUMENT, config).zip(batch).forEach { (vector, text) ->
                vectors[keys.getValue(text)] = VectorCodec.encode(vector)
            }
        }
        if (missing.isNotEmpty()) save()
        return keys.mapNotNull { (text, key) -> vectors[key]?.let(VectorCodec::decode)?.let { text to it } }.toMap()
    }

    private fun key(signature: String, text: String): String =
        MessageDigest.getInstance("SHA-1").digest("$signature\n$text".toByteArray()).joinToString("") { "%02x".format(it) }

    private fun load(): LinkedHashMap<String, String> {
        val stored = runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), cache.readText()) }.getOrNull()
        return object : LinkedHashMap<String, String>(stored.orEmpty()) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_VECTORS
        }
    }

    private fun save() {
        cache.parentFile?.mkdirs()
        cache.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), vectors))
    }

    private companion object {
        const val TAG = "LocalSearch"
        const val MAX_DEPTH = 8
        const val MAX_FILE_BYTES = 512L * 1024
        const val MAX_FILES = 1_500
        const val MAX_MESSAGES = 3_000
        const val MAX_MESSAGE_CHARS = 1_500
        const val SEMANTIC_POOL = 300
        const val BATCH = 64
        const val MAX_VECTORS = 8_000
    }
}
