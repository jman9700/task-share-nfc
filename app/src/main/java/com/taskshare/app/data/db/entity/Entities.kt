package com.taskshare.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "household_users")
data class HouseholdUserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val isLocal: Boolean,
)

fun HouseholdUserEntity.toDomain() = HouseholdUser(id, displayName, isLocal)
fun HouseholdUser.toEntity() = HouseholdUserEntity(id, displayName, isLocal)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val description: String,
    val location: String,
    /** Null means a one-time (non-repeating) task — see Task.frequency. */
    val frequencyQuantity: Int?,
    val frequencyUnit: FrequencyUnit?,
    val startDate: LocalDate,
    val ownerIds: List<String>,
    val priority: Priority,
    val createdAt: Instant,
    val archived: Boolean,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    name = name,
    description = description,
    location = location,
    frequency = if (frequencyQuantity != null && frequencyUnit != null) Frequency(frequencyQuantity, frequencyUnit) else null,
    startDate = startDate,
    ownerIds = ownerIds,
    priority = priority,
    createdAt = createdAt,
    archived = archived,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    description = description,
    location = location,
    frequencyQuantity = frequency?.quantity,
    frequencyUnit = frequency?.unit,
    startDate = startDate,
    ownerIds = ownerIds,
    priority = priority,
    createdAt = createdAt,
    archived = archived,
)

@Entity(tableName = "task_instances")
data class TaskInstanceEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val completedAt: Instant,
    val completedByUserId: String,
)

fun TaskInstanceEntity.toDomain() = TaskInstance(id, taskId, completedAt, completedByUserId)
fun TaskInstance.toEntity() = TaskInstanceEntity(id, taskId, completedAt, completedByUserId)
