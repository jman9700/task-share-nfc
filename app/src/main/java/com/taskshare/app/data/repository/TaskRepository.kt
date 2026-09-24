package com.taskshare.app.data.repository

import com.taskshare.app.data.db.HouseholdUserDao
import com.taskshare.app.data.db.TaskDao
import com.taskshare.app.data.db.TaskInstanceDao
import com.taskshare.app.data.db.entity.toDomain
import com.taskshare.app.data.db.entity.toEntity
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import com.taskshare.app.data.sync.SyncMerger
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.data.sync.SyncTaskDto
import com.taskshare.app.data.sync.SyncUserDto
import com.taskshare.app.data.sync.SyncInstanceDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao,
    private val instanceDao: TaskInstanceDao,
    private val userDao: HouseholdUserDao,
    private val localDeviceId: String,
) {
    fun observeActiveTasks(): Flow<List<Task>> = taskDao.observeActive().map { list -> list.map { it.toDomain() } }
    fun observeInstances(): Flow<List<TaskInstance>> = instanceDao.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeUsers(): Flow<List<HouseholdUser>> = userDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addLocalUser(user: HouseholdUser) = userDao.insert(user.toEntity())

    suspend fun hasLocalUser(): Boolean = userDao.getAll().any { it.isLocal }

    suspend fun createTask(
        name: String,
        description: String,
        location: String,
        frequency: Frequency?,
        startDate: LocalDate,
        ownerIds: List<String>,
        priority: Priority,
    ) {
        val task = Task(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            location = location,
            frequency = frequency,
            startDate = startDate,
            ownerIds = ownerIds,
            priority = priority,
            createdAt = Instant.now(),
        )
        taskDao.insert(task.toEntity())
    }

    suspend fun recordCompletion(taskId: String, completedByUserId: String, completedAt: Instant = Instant.now()) {
        instanceDao.insert(
            TaskInstance(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                completedAt = completedAt,
                completedByUserId = completedByUserId,
            ).toEntity()
        )
    }

    suspend fun archiveTask(taskId: String) = taskDao.archive(taskId)

    /** Snapshot of everything this device would send to a peer during a sync. */
    suspend fun buildOutgoingPayload(): SyncPayload {
        val users = userDao.getAll().map { SyncUserDto(it.id, it.displayName) }
        val tasks = taskDao.getAll().filterNot { it.archived }.map {
            val frequency = if (it.frequencyQuantity != null && it.frequencyUnit != null) {
                Frequency(it.frequencyQuantity, it.frequencyUnit)
            } else {
                null
            }
            SyncTaskDto(it.name, it.description, it.location, frequency, it.startDate, it.ownerIds, it.priority, it.createdAt)
        }
        val instances = instanceDao.getAll().map { SyncInstanceDto(it.id, taskNameFor(it.taskId), it.completedAt, it.completedByUserId) }
        return SyncPayload(localDeviceId, users, tasks, instances)
    }

    private var taskNameCache: Map<String, String> = emptyMap()
    private suspend fun taskNameFor(taskId: String): String {
        if (taskId !in taskNameCache) taskNameCache = taskDao.getAll().associate { it.id to it.name }
        return taskNameCache[taskId] ?: ""
    }

    /** Applies an incoming payload from a peer device additively. Never overwrites local rows. */
    suspend fun applyIncoming(payload: SyncPayload): com.taskshare.app.data.sync.MergeResult {
        val result = SyncMerger.merge(
            payload = payload,
            localUsers = userDao.getAll().map { it.toDomain() },
            localTasks = taskDao.getAll().map { it.toDomain() },
            localInstances = instanceDao.getAll().map { it.toDomain() },
        )
        userDao.insertAll(result.newUsers.map { it.toEntity() })
        taskDao.insertAll(result.newTasks.map { it.toEntity() })
        instanceDao.insertAll(result.newInstances.map { it.toEntity() })
        return result
    }
}
