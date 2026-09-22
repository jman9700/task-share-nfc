package com.taskshare.app.data.sync

import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.Priority
import java.time.Instant

/**
 * Wire format exchanged over the Bluetooth transport once the NFC handshake has paired two
 * devices. Deliberately a full snapshot of the sender's local data (not a delta) — dedupe on
 * the receiving side makes repeated syncs idempotent, which is simpler and safer than tracking
 * per-peer watermarks for a household-scale dataset. See SyncMerger for the merge rules.
 */
data class SyncPayload(
    val senderDeviceId: String,
    val users: List<SyncUserDto>,
    val tasks: List<SyncTaskDto>,
    val instances: List<SyncInstanceDto>,
)

data class SyncUserDto(
    val id: String,
    val displayName: String,
)

data class SyncTaskDto(
    val name: String,
    val description: String,
    val location: String,
    val frequency: Frequency,
    val ownerIds: List<String>,
    val priority: Priority,
    val createdAt: Instant,
)

data class SyncInstanceDto(
    val instanceId: String,
    val taskName: String,
    val completedAt: Instant,
    val completedByUserId: String,
)
