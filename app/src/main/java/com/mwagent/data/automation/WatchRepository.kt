package com.mwagent.data.automation

import com.mwagent.data.db.WatchDao
import com.mwagent.data.db.WatchEntity
import com.mwagent.data.db.WatchSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class WatchEvent(
    val source: WatchSource,
    val origin: String,
    val names: List<String>,
    val text: String,
    val direct: Boolean = true,
)

fun WatchEntity.matches(event: WatchEvent): Boolean {
    if (source != event.source) return false
    val who = sender.normalized()
    val byWho = if (who.isEmpty()) event.direct else event.names.any { it.normalized().let { name -> name == who || who in name } }
    return byWho && (keyword.isNullOrBlank() || event.text.contains(keyword.trim(), ignoreCase = true))
}

private fun String?.normalized(): String = orEmpty().trim().removePrefix("@").lowercase()

class WatchRepository(private val dao: WatchDao, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeAll(): Flow<List<WatchEntity>> = dao.observeAll()

    suspend fun list(): List<WatchEntity> = dao.listAll()

    suspend fun get(id: String): WatchEntity? = dao.get(id)

    suspend fun create(title: String, source: WatchSource, sender: String?, keyword: String?, instruction: String, chatId: String): WatchEntity {
        require(title.isNotBlank()) { "The watch title is empty" }
        require(instruction.isNotBlank()) { "The instruction is empty" }
        require(instruction.length <= MAX_INSTRUCTION) { "The instruction is longer than $MAX_INSTRUCTION characters" }
        require(dao.listAll().size < MAX_WATCHES) { "There are already $MAX_WATCHES watches, remove one first" }
        val watch = WatchEntity(
            id = UUID.randomUUID().toString().take(ID_LENGTH),
            title = title.trim().take(MAX_TITLE),
            source = source,
            sender = sender?.trim()?.takeIf { it.isNotEmpty() },
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
            instruction = instruction.trim(),
            chatId = chatId,
            createdAt = clock(),
        )
        dao.insert(watch)
        return watch
    }

    suspend fun edit(id: String, title: String, sender: String?, keyword: String?, instruction: String) {
        require(title.isNotBlank()) { "The watch title is empty" }
        require(instruction.isNotBlank()) { "The instruction is empty" }
        require(instruction.length <= MAX_INSTRUCTION) { "The instruction is longer than $MAX_INSTRUCTION characters" }
        val watch = dao.get(id) ?: return
        dao.upsert(
            watch.copy(
                title = title.trim().take(MAX_TITLE),
                sender = sender?.trim()?.takeIf { it.isNotEmpty() },
                keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
                instruction = instruction.trim(),
            ),
        )
    }

    suspend fun setPaused(id: String, paused: Boolean) {
        dao.get(id)?.let { dao.upsert(it.copy(paused = paused)) }
    }

    suspend fun delete(id: String): Boolean = dao.delete(id) > 0

    suspend fun moveTo(id: String, chatId: String) = dao.moveTo(id, chatId)

    companion object {
        const val MAX_INSTRUCTION = 1_000
        private const val MAX_WATCHES = 20
        private const val MAX_TITLE = 80
        private const val ID_LENGTH = 8
    }
}
