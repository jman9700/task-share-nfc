package com.taskshare.app.data.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

fun Task.lastCompletedAt(instances: List<TaskInstance>): Instant? =
    instances.filter { it.taskId == id }.maxByOrNull { it.completedAt }?.completedAt

/** [Task.startDate] as a point in time, at the start of that day in [zone]. */
fun Task.startInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = startDate.atStartOfDay(zone).toInstant()

fun Task.hasStarted(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
    !startInstant(zone).isAfter(now)

/**
 * How "due" a task is, relative to how often it's supposed to happen — counted from
 * [Task.startDate], not [Task.createdAt], so a task you set up today to start next month doesn't
 * read as freshly-done today. 1.0 means exactly on schedule; >1.0 means overdue.
 *
 * A one-time task (frequency == null) uses a fixed 1-day reference interval purely to give it a
 * meaningful, ever-increasing "how overdue" number for the main list's sort — this has no effect
 * on calendar projections, which special-case one-time tasks separately (see CalendarProjection).
 */
fun Task.urgencyScore(instances: List<TaskInstance>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Double {
    val since = lastCompletedAt(instances) ?: startInstant(zone)
    val daysSince = Duration.between(since, now).toMinutes() / (24.0 * 60.0)
    val intervalDays = frequency?.intervalDays ?: 1.0
    return (daysSince / intervalDays).coerceAtLeast(0.0)
}

/**
 * When this task's next occurrence is due. For a one-time task this is always [Task.startDate]
 * itself — it has no further occurrences, so callers that care whether it's already been done
 * (CalendarProjection) check that separately rather than relying on this to reflect it.
 */
fun Task.nextDueAt(instances: List<TaskInstance>, zone: ZoneId = ZoneId.systemDefault()): Instant {
    val intervalDays = frequency?.intervalDays ?: return startInstant(zone)
    val base = lastCompletedAt(instances) ?: startInstant(zone)
    return base.plus(Duration.ofMinutes((intervalDays * 24 * 60).toLong()))
}
