package com.taskshare.app.data.sync

import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import java.util.UUID

/**
 * Result of applying an incoming [SyncPayload] to local state. Everything here is additive:
 * existing local rows are never modified or removed, only new rows are proposed. The caller
 * (TaskRepository) is responsible for persisting these via Room.
 */
data class MergeResult(
    val newUsers: List<HouseholdUser>,
    val newTasks: List<Task>,
    val newInstances: List<TaskInstance>,
    /** Instances whose task name matched nothing locally (e.g. the task was archived here). */
    val unresolvedInstances: List<SyncInstanceDto>,
)

/**
 * Pure merge function — no Room/Android dependencies — so the sync rules can be unit tested
 * without an emulator. Matching rules (from design discussion):
 *  - Tasks match by normalized name. A new task in the payload whose name doesn't exist locally
 *    is inserted as-is, with its full definition (name, location, owners, frequency, priority).
 *  - A task whose name already exists locally is left completely alone: location/owner/priority/
 *    frequency edits are intentionally device-local and never overwritten by sync.
 *  - Instances (completions) match by their stable instanceId. New ones are inserted; ones
 *    already known locally are skipped so re-syncing is idempotent.
 *  - Users match by id and are inserted additively so the other partner's display name is known.
 */
object SyncMerger {

    fun merge(
        payload: SyncPayload,
        localUsers: List<HouseholdUser>,
        localTasks: List<Task>,
        localInstances: List<TaskInstance>,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): MergeResult {
        val localUserIds = localUsers.map { it.id }.toSet()
        val newUsers = payload.users
            .filter { it.id !in localUserIds }
            .map { HouseholdUser(id = it.id, displayName = it.displayName, isLocal = false) }

        val localTaskByName = localTasks.associateBy { it.normalizedName }
        val newTasks = payload.tasks
            .filter { Task.normalizeName(it.name) !in localTaskByName }
            // A payload can (rarely) contain the same new task name twice; keep the first.
            .distinctBy { Task.normalizeName(it.name) }
            .map { dto ->
                Task(
                    id = newId(),
                    name = dto.name,
                    description = dto.description,
                    location = dto.location,
                    frequency = dto.frequency,
                    ownerIds = dto.ownerIds,
                    priority = dto.priority,
                    createdAt = dto.createdAt,
                )
            }

        // Tasks now known locally, by name, including ones this merge is about to add.
        val allKnownTaskByName = localTaskByName + newTasks.associateBy { it.normalizedName }

        val localInstanceIds = localInstances.map { it.id }.toSet()
        val newInstances = mutableListOf<TaskInstance>()
        val unresolved = mutableListOf<SyncInstanceDto>()
        for (dto in payload.instances) {
            if (dto.instanceId in localInstanceIds) continue // already have it
            val task = allKnownTaskByName[Task.normalizeName(dto.taskName)]
            if (task == null) {
                unresolved += dto
                continue
            }
            newInstances += TaskInstance(
                id = dto.instanceId,
                taskId = task.id,
                completedAt = dto.completedAt,
                completedByUserId = dto.completedByUserId,
            )
        }

        return MergeResult(newUsers, newTasks, newInstances, unresolved)
    }
}
