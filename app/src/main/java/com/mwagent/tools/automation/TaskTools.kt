package com.mwagent.tools.automation

import com.mwagent.data.automation.TaskRepository
import com.mwagent.data.automation.TaskSchedule
import com.mwagent.data.db.TaskEntity
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.tools.ContextualTool
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolContext
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalArray
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.reminders.ReminderClock
import com.mwagent.tools.requireString
import com.mwagent.tools.stringArrayProperty
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek

private fun TaskEntity.describe(clock: ReminderClock): String {
    val repeat = when {
        everyMinutes != null -> "every $everyMinutes min"
        days != 0 -> "weekly on " + TaskSchedule.days(days).joinToString { it.name.lowercase() }
        else -> "once"
    }
    val state = if (paused) ", paused" else ""
    return "[$id] \"$title\", next ${clock.format(triggerAt)}, $repeat$state: ${prompt.take(PROMPT_PREVIEW)}"
}

private const val PROMPT_PREVIEW = 120

class ScheduleTaskTool(private val tasks: TaskRepository, private val clock: ReminderClock = ReminderClock()) : ContextualTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Schedules a task you will do on your own later, once or on repeat: at that moment you get the prompt, run it " +
            "with your tools and the user gets your reply as a notification. Use it for things like a morning brief, checking a " +
            "price or summarizing unread chats; for a plain note at some moment use set_reminder instead. Give the first run in " +
            "\"at\" or \"in_minutes\". To repeat, give \"days\" for weekly runs at the same time, or \"every_minutes\". Write the " +
            "prompt as a full instruction to yourself, because it runs without this conversation. By default results go to a " +
            "chat of the task's own; set \"here\" to post them in this chat.",
        parameters = objectSchema(required = listOf("title", "prompt")) {
            stringProperty("title", "Short name shown in the notification, in the user's language")
            stringProperty("prompt", "What to do on each run, up to ${TaskRepository.MAX_PROMPT} characters")
            stringProperty("at", "First run, local date and time like 2026-10-08T08:00")
            integerProperty("in_minutes", "First run in this many minutes from now", minimum = 1, maximum = MAX_MINUTES)
            stringArrayProperty("days", "Weekdays to repeat on at the time of the first run, English names like MONDAY")
            integerProperty("every_minutes", "Repeat interval in minutes", minimum = TaskRepository.MIN_EVERY, maximum = TaskRepository.MAX_EVERY)
            booleanProperty("here", "Post results in this chat instead of the task's own chat")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        val at = arguments.optionalString("at")
        val minutes = arguments.optionalInt("in_minutes")
        val start = when {
            at != null && minutes != null -> throw ToolArgumentException("Give either at or in_minutes, not both")
            at != null -> clock.parseLocal(at)
            minutes != null -> clock.now() + minutes.coerceIn(1, MAX_MINUTES) * MS_PER_MINUTE
            else -> throw ToolArgumentException("Either at or in_minutes is required")
        }
        val days = TaskSchedule.mask(arguments.optionalArray("days").map { parseDay((it as? JsonPrimitive)?.contentOrNull) })
        val chatId = if (arguments.optionalBoolean("here") == true) context.chatId else ""
        val task = try {
            tasks.create(arguments.requireString("title"), arguments.requireString("prompt"), chatId, start, days, arguments.optionalInt("every_minutes"))
        } catch (e: IllegalArgumentException) {
            return ToolOutcome.error(e.message.orEmpty())
        }
        val notes = buildList {
            add("Task scheduled ${task.describe(clock)}")
            if (!tasks.canScheduleExact) add("Exact alarms are not allowed, so runs may start a few minutes late.")
        }
        return ToolOutcome(notes.joinToString("\n"))
    }

    private fun parseDay(raw: String?): DayOfWeek =
        DayOfWeek.entries.firstOrNull { raw != null && it.name.startsWith(raw.trim().uppercase().take(DAY_PREFIX)) }
            ?: throw ToolArgumentException("Unknown weekday \"$raw\"")

    companion object {
        const val NAME = "schedule_task"
        private const val MAX_MINUTES = 525_600
        private const val MS_PER_MINUTE = 60_000L
        private const val DAY_PREFIX = 3
    }
}

class ListTasksTool(private val tasks: TaskRepository, private val clock: ReminderClock = ReminderClock()) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists scheduled tasks with their ids, next run and repeat, soonest first.",
        parameters = objectSchema(required = emptyList()) {},
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val list = tasks.list()
        if (list.isEmpty()) return ToolOutcome("No scheduled tasks")
        return ToolOutcome(list.joinToString("\n") { it.describe(clock) })
    }

    companion object {
        const val NAME = "list_tasks"
    }
}

class CancelTaskTool(private val tasks: TaskRepository) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Cancels a scheduled task by its id from ${ListTasksTool.NAME}.",
        parameters = objectSchema(required = listOf("id")) {
            stringProperty("id", "Task id")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val id = arguments.requireString("id")
        return if (tasks.cancel(id)) ToolOutcome("Task $id cancelled") else ToolOutcome.error("No scheduled task with id $id")
    }

    companion object {
        const val NAME = "cancel_task"
    }
}
