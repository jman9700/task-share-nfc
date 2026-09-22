package com.taskshare.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class UrgencyTest {

    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val weekly = Task(
        id = "t1", name = "Dishes", description = "", location = "Kitchen",
        frequency = Frequency(1, FrequencyUnit.WEEK), ownerIds = emptyList(),
        priority = Priority.MEDIUM, createdAt = createdAt,
    )

    @Test
    fun `never-done task is scored from creation date, not treated as infinitely overdue`() {
        val now = createdAt.plus(3, ChronoUnit.DAYS)
        val score = weekly.urgencyScore(emptyList(), now)
        assertEquals(3.0 / 7.0, score, 0.001)
    }

    @Test
    fun `task done exactly on schedule scores 1_0`() {
        val lastDone = createdAt.plus(7, ChronoUnit.DAYS)
        val now = lastDone.plus(7, ChronoUnit.DAYS)
        val instance = TaskInstance("i1", "t1", lastDone, "u1")
        val score = weekly.urgencyScore(listOf(instance), now)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `overdue task scores above 1_0 and more overdue tasks score higher`() {
        val lastDone = createdAt
        val instance = TaskInstance("i1", "t1", lastDone, "u1")
        val slightlyOverdue = weekly.urgencyScore(listOf(instance), lastDone.plus(10, ChronoUnit.DAYS))
        val veryOverdue = weekly.urgencyScore(listOf(instance), lastDone.plus(30, ChronoUnit.DAYS))
        assertTrue(slightlyOverdue > 1.0)
        assertTrue(veryOverdue > slightlyOverdue)
    }

    @Test
    fun `only instances of this task affect its score`() {
        val otherTaskInstance = TaskInstance("i1", "other-task", createdAt.plus(6, ChronoUnit.DAYS), "u1")
        val now = createdAt.plus(10, ChronoUnit.DAYS)
        val score = weekly.urgencyScore(listOf(otherTaskInstance), now)
        // Should fall back to createdAt since no instance matches t1's id.
        assertEquals(10.0 / 7.0, score, 0.001)
    }
}
