package com.taskshare.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class UrgencyTest {

    private val zone = ZoneOffset.UTC
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val startDate = LocalDate.of(2026, 1, 1) // matches createdAt exactly, at UTC
    private val weekly = Task(
        id = "t1", name = "Dishes", description = "", location = "Kitchen",
        frequency = Frequency(1, FrequencyUnit.WEEK), startDate = startDate, ownerIds = emptyList(),
        priority = Priority.MEDIUM, createdAt = createdAt,
    )

    @Test
    fun `never-done task is scored from start date, not treated as infinitely overdue`() {
        val now = createdAt.plus(3, ChronoUnit.DAYS)
        val score = weekly.urgencyScore(emptyList(), now, zone)
        assertEquals(3.0 / 7.0, score, 0.001)
    }

    @Test
    fun `task done exactly on schedule scores 1_0`() {
        val lastDone = createdAt.plus(7, ChronoUnit.DAYS)
        val now = lastDone.plus(7, ChronoUnit.DAYS)
        val instance = TaskInstance("i1", "t1", lastDone, "u1")
        val score = weekly.urgencyScore(listOf(instance), now, zone)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `overdue task scores above 1_0 and more overdue tasks score higher`() {
        val lastDone = createdAt
        val instance = TaskInstance("i1", "t1", lastDone, "u1")
        val slightlyOverdue = weekly.urgencyScore(listOf(instance), lastDone.plus(10, ChronoUnit.DAYS), zone)
        val veryOverdue = weekly.urgencyScore(listOf(instance), lastDone.plus(30, ChronoUnit.DAYS), zone)
        assertTrue(slightlyOverdue > 1.0)
        assertTrue(veryOverdue > slightlyOverdue)
    }

    @Test
    fun `only instances of this task affect its score`() {
        val otherTaskInstance = TaskInstance("i1", "other-task", createdAt.plus(6, ChronoUnit.DAYS), "u1")
        val now = createdAt.plus(10, ChronoUnit.DAYS)
        val score = weekly.urgencyScore(listOf(otherTaskInstance), now, zone)
        // Should fall back to startDate since no instance matches t1's id.
        assertEquals(10.0 / 7.0, score, 0.001)
    }

    @Test
    fun `a task whose start date hasn't arrived yet has not started`() {
        val futureTask = weekly.copy(startDate = LocalDate.of(2026, 6, 1))
        assertTrue(!futureTask.hasStarted(now = createdAt, zone = zone))
    }

    @Test
    fun `a task whose start date has passed has started`() {
        assertTrue(weekly.hasStarted(now = createdAt.plus(1, ChronoUnit.DAYS), zone = zone))
    }

    @Test
    fun `a one-time task uses a fixed 1-day reference interval`() {
        val oneTime = weekly.copy(frequency = null)
        val now = createdAt.plus(2, ChronoUnit.DAYS)
        assertEquals(2.0, oneTime.urgencyScore(emptyList(), now, zone), 0.001)
    }
}
