package com.mwagent.data.automation

import com.mwagent.data.db.TaskDao
import com.mwagent.data.db.TaskEntity
import com.mwagent.system.automation.TaskAlarms
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import java.util.UUID

class TaskRepository(
    private val dao: TaskDao,
    private val alarms: TaskAlarms,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val canScheduleExact: Boolean get() = alarms.canScheduleExact

    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun list(): List<TaskEntity> = dao.listAll()

    suspend fun create(title: String, prompt: String, chatId: String, start: Long, days: Int, every: Int?): TaskEntity {
        val now = clock()
        require(title.isNotBlank()) { "The task title is empty" }
        require(prompt.isNotBlank()) { "The task prompt is empty" }
        require(prompt.length <= MAX_PROMPT) { "The task prompt is longer than $MAX_PROMPT characters" }
        require(every == null || every in MIN_EVERY..MAX_EVERY) { "Repeats must be between $MIN_EVERY minutes and a week apart" }
        require(every == null || days == 0) { "Give either days or every_minutes, not both" }
        require(start - now <= MAX_AHEAD_MS) { "Tasks can start at most a year ahead" }
        val first = TaskSchedule.next(start, days, every, now, zone()) ?: throw IllegalArgumentException("The task time is in the past")
        val task = TaskEntity(
            id = UUID.randomUUID().toString().take(ID_LENGTH),
            title = title.trim().take(MAX_TITLE),
            prompt = prompt.trim(),
            chatId = chatId,
            triggerAt = first,
            days = days,
            everyMinutes = every,
            createdAt = now,
        )
        dao.upsert(task)
        alarms.schedule(task.id, task.triggerAt)
        return task
    }

    suspend fun cancel(id: String): Boolean {
        alarms.cancel(id)
        return dao.delete(id) > 0
    }

    suspend fun moveTo(id: String, chatId: String) = dao.moveTo(id, chatId)

    suspend fun fire(id: String): TaskEntity? {
        val task = dao.get(id) ?: return null
        val now = clock()
        advance(task, now)
        val repeats = task.days != 0 || task.everyMinutes != null
        return task.takeUnless { repeats && now - task.triggerAt > STALE_MS }
    }

    suspend fun restore(): List<TaskEntity> {
        val now = clock()
        val (due, later) = dao.listAll().partition { it.triggerAt <= now }
        later.forEach { alarms.schedule(it.id, it.triggerAt) }
        return due.mapNotNull { fire(it.id) }
    }

    private suspend fun advance(task: TaskEntity, now: Long) {
        val next = TaskSchedule.next(task, now, zone())
        if (next == null) {
            dao.delete(task.id)
        } else {
            dao.upsert(task.copy(triggerAt = next))
            alarms.schedule(task.id, next)
        }
    }

    companion object {
        const val MAX_PROMPT = 2_000
        const val MIN_EVERY = 15
        const val MAX_EVERY = 7 * 24 * 60
        private const val MAX_TITLE = 80
        private const val ID_LENGTH = 8
        private const val STALE_MS = 2L * 60 * 60 * 1000
        private const val MAX_AHEAD_MS = 366L * 24 * 60 * 60 * 1000
    }
}
