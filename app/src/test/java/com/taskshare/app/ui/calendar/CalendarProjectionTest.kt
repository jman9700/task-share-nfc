package com.taskshare.app.ui.calendar

import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CalendarProjectionTest {

    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `daily task over a week projects about seven upcoming occurrences`() {
        val task = Task("t1", "Water plants", "", "Kitchen", Frequency(1, FrequencyUnit.DAY), emptyList(), Priority.LOW, createdAt)
        val rangeStart = createdAt
        val rangeEnd = createdAt.plus(7, ChronoUnit.DAYS)

        val entries = CalendarProjection.buildEntries(listOf(task), emptyList(), rangeStart, rangeEnd)

        assertTrue(entries.all { it.type == CalendarEntryType.UPCOMING })
        assertTrue("expected roughly 7 daily occurrences, got ${entries.size}", entries.size in 6..8)
    }

    @Test
    fun `completed instance within range is included as COMPLETED`() {
        val task = Task("t1", "Dishes", "", "Kitchen", Frequency(1, FrequencyUnit.WEEK), emptyList(), Priority.LOW, createdAt)
        val instance = TaskInstance("i1", "t1", createdAt.plus(2, ChronoUnit.DAYS), "u1")

        val entries = CalendarProjection.buildEntries(
            listOf(task), listOf(instance), createdAt, createdAt.plus(7, ChronoUnit.DAYS)
        )

        assertTrue(entries.any { it.type == CalendarEntryType.COMPLETED && it.taskId == "t1" })
    }

    @Test
    fun `entries outside the range are excluded`() {
        val task = Task("t1", "Dishes", "", "Kitchen", Frequency(1, FrequencyUnit.WEEK), emptyList(), Priority.LOW, createdAt)
        val farFutureInstance = TaskInstance("i1", "t1", createdAt.plus(90, ChronoUnit.DAYS), "u1")

        val entries = CalendarProjection.buildEntries(
            listOf(task), listOf(farFutureInstance), createdAt, createdAt.plus(7, ChronoUnit.DAYS)
        )

        assertTrue(entries.none { it.type == CalendarEntryType.COMPLETED })
    }

    @Test
    fun `entries are sorted chronologically`() {
        val taskA = Task("a", "A", "", "", Frequency(3, FrequencyUnit.DAY), emptyList(), Priority.LOW, createdAt)
        val taskB = Task("b", "B", "", "", Frequency(2, FrequencyUnit.DAY), emptyList(), Priority.LOW, createdAt)

        val entries = CalendarProjection.buildEntries(
            listOf(taskA, taskB), emptyList(), createdAt, createdAt.plus(10, ChronoUnit.DAYS)
        )

        val sorted = entries.map { it.at }
        assertEquals(sorted, sorted.sorted())
    }
}
