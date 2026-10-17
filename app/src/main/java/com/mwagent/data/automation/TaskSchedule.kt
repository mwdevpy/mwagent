package com.mwagent.data.automation

import com.mwagent.data.db.TaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

object TaskSchedule {
    fun next(task: TaskEntity, after: Long, zone: ZoneId): Long? = next(task.triggerAt, task.days, task.everyMinutes, after, zone)

    fun next(anchor: Long, days: Int, every: Int?, after: Long, zone: ZoneId): Long? = when {
        every != null -> {
            val step = every * MS_PER_MINUTE
            if (anchor > after) anchor else anchor + ((after - anchor) / step + 1) * step
        }
        days != 0 -> weekly(anchor, days, after, zone)
        else -> anchor.takeIf { it > after }
    }

    fun mask(days: Collection<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl day.ordinal) }

    fun days(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }

    private fun weekly(anchor: Long, days: Int, after: Long, zone: ZoneId): Long? {
        val start = Instant.ofEpochMilli(anchor).atZone(zone)
        val time = start.toLocalTime()
        val from = maxOf(start.toLocalDate(), Instant.ofEpochMilli(after).atZone(zone).toLocalDate())
        return (0L..DAYS_AHEAD).asSequence()
            .map(from::plusDays)
            .filter { days and (1 shl it.dayOfWeek.ordinal) != 0 }
            .map { it.atTime(time).atZone(zone).toInstant().toEpochMilli() }
            .firstOrNull { it > after }
    }

    private const val MS_PER_MINUTE = 60_000L
    private const val DAYS_AHEAD = 8L
}
