package com.taskshare.app.data.model

import java.time.Duration
import java.time.Instant

/**
 * How "due" a task is, relative to how often it's supposed to happen.
 * 1.0 means exactly on schedule; >1.0 means overdue; a never-done task is scored from its
 * creation date so brand-new tasks aren't immediately flagged as maximally overdue.
 */
fun Task.urgencyScore(instances: List<TaskInstance>, now: Instant = Instant.now()): Double {
    val lastDone = instances
        .filter { it.taskId == id }
        .maxByOrNull { it.completedAt }
        ?.completedAt
    val since = lastDone ?: createdAt
    val daysSince = Duration.between(since, now).toMinutes() / (24.0 * 60.0)
    return (daysSince / frequency.intervalDays).coerceAtLeast(0.0)
}

fun Task.lastCompletedAt(instances: List<TaskInstance>): Instant? =
    instances.filter { it.taskId == id }.maxByOrNull { it.completedAt }?.completedAt

fun Task.nextDueAt(instances: List<TaskInstance>): Instant {
    val base = lastCompletedAt(instances) ?: createdAt
    return base.plus(Duration.ofMinutes((frequency.intervalDays * 24 * 60).toLong()))
}
