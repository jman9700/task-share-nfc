package com.taskshare.app.ui.calendar

import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import com.taskshare.app.data.model.nextDueAt
import java.time.Duration
import java.time.Instant

enum class CalendarEntryType { COMPLETED, UPCOMING }

data class CalendarEntry(
    val taskId: String,
    val taskName: String,
    val at: Instant,
    val type: CalendarEntryType,
    val completedByUserId: String? = null,
)

/**
 * Pure, unit-testable projection of what a task calendar should show in [rangeStart, rangeEnd):
 * every actual completion in range, plus every future occurrence implied by each task's
 * frequency (there can be more than one in a wide-enough range, e.g. a daily task over a month).
 */
object CalendarProjection {

    fun buildEntries(
        tasks: List<Task>,
        instances: List<TaskInstance>,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): List<CalendarEntry> {
        val entries = mutableListOf<CalendarEntry>()

        val instancesByTask = instances.groupBy { it.taskId }
        for (instance in instances) {
            if (instance.completedAt >= rangeStart && instance.completedAt < rangeEnd) {
                val taskName = tasks.firstOrNull { it.id == instance.taskId }?.name ?: "(deleted task)"
                entries += CalendarEntry(instance.taskId, taskName, instance.completedAt, CalendarEntryType.COMPLETED, instance.completedByUserId)
            }
        }

        for (task in tasks) {
            val taskInstances = instancesByTask[task.id].orEmpty()
            val intervalMinutes = (task.frequency.intervalDays * 24 * 60).toLong().coerceAtLeast(1)
            var next = task.nextDueAt(taskInstances)
            // Walk forward through the range; bounded by range width / interval so a
            // misconfigured (very short) frequency can't spin forever.
            var guard = 0
            while (next < rangeEnd && guard < 10_000) {
                if (next >= rangeStart) {
                    entries += CalendarEntry(task.id, task.name, next, CalendarEntryType.UPCOMING)
                }
                next = next.plus(Duration.ofMinutes(intervalMinutes))
                guard++
            }
        }

        return entries.sortedBy { it.at }
    }
}
