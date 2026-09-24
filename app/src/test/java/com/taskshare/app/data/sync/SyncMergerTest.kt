package com.taskshare.app.data.sync

import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncMergerTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val startDate = LocalDate.of(2026, 1, 1)
    private val weekly = Frequency(1, FrequencyUnit.WEEK)

    @Test
    fun `brand new task from payload is inserted`() {
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = listOf(
                SyncTaskDto("Dishes", "", "Kitchen", weekly, startDate, listOf("u1"), Priority.MEDIUM, now)
            ),
            instances = emptyList(),
        )

        val result = SyncMerger.merge(payload, emptyList(), emptyList(), emptyList(), newId = { "generated-1" })

        assertEquals(1, result.newTasks.size)
        assertEquals("Dishes", result.newTasks[0].name)
        assertEquals("generated-1", result.newTasks[0].id)
    }

    @Test
    fun `tasks with the same normalized name on both devices merge into one, local wins`() {
        val local = Task(
            id = "local-1", name = "dishes", description = "", location = "Kitchen (mine)",
            frequency = weekly, startDate = startDate, ownerIds = listOf("local-owner"), priority = Priority.LOW, createdAt = now,
        )
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = listOf(
                SyncTaskDto("  Dishes ", "", "Kitchen (theirs)", weekly, startDate, listOf("remote-owner"), Priority.HIGH, now)
            ),
            instances = emptyList(),
        )

        val result = SyncMerger.merge(payload, emptyList(), listOf(local), emptyList())

        assertTrue("no new task should be inserted; name already matches", result.newTasks.isEmpty())
    }

    @Test
    fun `new completion instance attaches to the locally-matching task by name`() {
        val local = Task(
            id = "local-1", name = "Dishes", description = "", location = "Kitchen",
            frequency = weekly, startDate = startDate, ownerIds = emptyList(), priority = Priority.LOW, createdAt = now,
        )
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = emptyList(),
            instances = listOf(
                SyncInstanceDto("instance-1", "Dishes", now, "u1")
            ),
        )

        val result = SyncMerger.merge(payload, emptyList(), listOf(local), emptyList())

        assertEquals(1, result.newInstances.size)
        assertEquals("local-1", result.newInstances[0].taskId)
        assertTrue(result.unresolvedInstances.isEmpty())
    }

    @Test
    fun `re-syncing the same payload twice never duplicates instances`() {
        val local = Task(
            id = "local-1", name = "Dishes", description = "", location = "Kitchen",
            frequency = weekly, startDate = startDate, ownerIds = emptyList(), priority = Priority.LOW, createdAt = now,
        )
        val existingInstance = TaskInstance("instance-1", "local-1", now, "u1")
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = emptyList(),
            instances = listOf(SyncInstanceDto("instance-1", "Dishes", now, "u1")),
        )

        val result = SyncMerger.merge(payload, emptyList(), listOf(local), listOf(existingInstance))

        assertTrue(result.newInstances.isEmpty())
    }

    @Test
    fun `instance for a task the receiver has archived away is reported unresolved, not dropped silently`() {
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = emptyList(),
            instances = listOf(SyncInstanceDto("instance-1", "Nonexistent Task", now, "u1")),
        )

        val result = SyncMerger.merge(payload, emptyList(), emptyList(), emptyList())

        assertTrue(result.newInstances.isEmpty())
        assertEquals(1, result.unresolvedInstances.size)
    }

    @Test
    fun `unknown remote user is added additively`() {
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = listOf(SyncUserDto("u-remote", "Alex")),
            tasks = emptyList(),
            instances = emptyList(),
        )

        val result = SyncMerger.merge(payload, listOf(HouseholdUser("u-local", "Sam", isLocal = true)), emptyList(), emptyList())

        assertEquals(1, result.newUsers.size)
        assertEquals("Alex", result.newUsers[0].displayName)
    }

    @Test
    fun `task and its instance arriving in the same payload both apply`() {
        val payload = SyncPayload(
            senderDeviceId = "device-b",
            users = emptyList(),
            tasks = listOf(SyncTaskDto("Vacuum", "", "Living room", weekly, startDate, emptyList(), Priority.MEDIUM, now)),
            instances = listOf(SyncInstanceDto("instance-1", "Vacuum", now, "u1")),
        )

        val result = SyncMerger.merge(payload, emptyList(), emptyList(), emptyList(), newId = { "new-task-id" })

        assertEquals(1, result.newTasks.size)
        assertEquals(1, result.newInstances.size)
        assertEquals("new-task-id", result.newInstances[0].taskId)
        assertTrue(result.unresolvedInstances.isEmpty())
    }
}
