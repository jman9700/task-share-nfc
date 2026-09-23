package com.taskshare.app.transport

import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.sync.SyncInstanceDto
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.data.sync.SyncTaskDto
import com.taskshare.app.data.sync.SyncUserDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SyncPayloadCodecTest {

    @Test
    fun `round trip preserves an empty payload`() {
        val payload = SyncPayload("device-a", emptyList(), emptyList(), emptyList())
        assertEquals(payload, SyncPayloadCodec.decode(SyncPayloadCodec.encode(payload)))
    }

    @Test
    fun `round trip preserves a payload with users, tasks, and instances`() {
        val payload = SyncPayload(
            senderDeviceId = "device-a",
            users = listOf(SyncUserDto("u1", "Sam"), SyncUserDto("u2", "Alex")),
            tasks = listOf(
                SyncTaskDto(
                    name = "Dishes",
                    description = "Load and run the dishwasher",
                    location = "Kitchen",
                    frequency = Frequency(2, FrequencyUnit.WEEK),
                    ownerIds = listOf("u1", "u2"),
                    priority = Priority.HIGH,
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
                SyncTaskDto(
                    name = "Water plants",
                    description = "",
                    location = "",
                    frequency = Frequency(1, FrequencyUnit.DAY),
                    ownerIds = emptyList(),
                    priority = Priority.LOW,
                    createdAt = Instant.parse("2026-01-02T12:30:00Z"),
                ),
            ),
            instances = listOf(
                SyncInstanceDto("i1", "Dishes", Instant.parse("2026-01-03T08:00:00Z"), "u1"),
                SyncInstanceDto("i2", "Water plants", Instant.parse("2026-01-04T09:15:00Z"), "u2"),
            ),
        )

        assertEquals(payload, SyncPayloadCodec.decode(SyncPayloadCodec.encode(payload)))
    }

    @Test
    fun `round trip preserves unicode and delimiter-like characters in text fields`() {
        val payload = SyncPayload(
            senderDeviceId = "device-a",
            users = listOf(SyncUserDto("u1", "Renée | Björk")),
            tasks = listOf(
                SyncTaskDto(
                    name = "Clean up | tidy",
                    description = "Uses a pipe | and emoji 🧹",
                    location = "Attic\nUpstairs",
                    frequency = Frequency(3, FrequencyUnit.MONTH),
                    ownerIds = listOf("u1"),
                    priority = Priority.MEDIUM,
                    createdAt = Instant.parse("2026-02-01T00:00:00Z"),
                )
            ),
            instances = emptyList(),
        )

        assertEquals(payload, SyncPayloadCodec.decode(SyncPayloadCodec.encode(payload)))
    }
}
