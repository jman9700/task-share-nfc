package com.taskshare.app.transport

import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.sync.SyncInstanceDto
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.data.sync.SyncTaskDto
import com.taskshare.app.data.sync.SyncUserDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Wire format for [SyncPayload]: plain length-prefixed java.io framing (DataOutputStream's
 * writeUTF/writeInt/writeLong), not JSON — no new dependency, no delimiter-escaping to get
 * wrong, and it's pure JVM code so it's unit-testable without an Android device or emulator
 * (see SyncPayloadCodecTest). Field order below must match encode/decode exactly.
 */
object SyncPayloadCodec {

    fun encode(payload: SyncPayload): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(payload.senderDeviceId)

            out.writeInt(payload.users.size)
            for (user in payload.users) {
                out.writeUTF(user.id)
                out.writeUTF(user.displayName)
            }

            out.writeInt(payload.tasks.size)
            for (task in payload.tasks) {
                out.writeUTF(task.name)
                out.writeUTF(task.description)
                out.writeUTF(task.location)
                out.writeBoolean(task.frequency != null)
                task.frequency?.let {
                    out.writeInt(it.quantity)
                    out.writeUTF(it.unit.name)
                }
                out.writeLong(task.startDate.toEpochDay())
                out.writeInt(task.ownerIds.size)
                for (ownerId in task.ownerIds) out.writeUTF(ownerId)
                out.writeUTF(task.priority.name)
                out.writeLong(task.createdAt.toEpochMilli())
            }

            out.writeInt(payload.instances.size)
            for (instance in payload.instances) {
                out.writeUTF(instance.instanceId)
                out.writeUTF(instance.taskName)
                out.writeLong(instance.completedAt.toEpochMilli())
                out.writeUTF(instance.completedByUserId)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): SyncPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val senderDeviceId = input.readUTF()

            val users = List(input.readInt()) {
                SyncUserDto(id = input.readUTF(), displayName = input.readUTF())
            }

            val tasks = List(input.readInt()) {
                val name = input.readUTF()
                val description = input.readUTF()
                val location = input.readUTF()
                val frequency = if (input.readBoolean()) {
                    Frequency(input.readInt(), FrequencyUnit.valueOf(input.readUTF()))
                } else {
                    null
                }
                val startDate = LocalDate.ofEpochDay(input.readLong())
                val ownerIds = List(input.readInt()) { input.readUTF() }
                val priority = Priority.valueOf(input.readUTF())
                val createdAt = Instant.ofEpochMilli(input.readLong())
                SyncTaskDto(name, description, location, frequency, startDate, ownerIds, priority, createdAt)
            }

            val instances = List(input.readInt()) {
                SyncInstanceDto(
                    instanceId = input.readUTF(),
                    taskName = input.readUTF(),
                    completedAt = Instant.ofEpochMilli(input.readLong()),
                    completedByUserId = input.readUTF(),
                )
            }

            return SyncPayload(senderDeviceId, users, tasks, instances)
        }
    }
}
