package com.taskshare.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import com.taskshare.app.data.model.hasStarted
import com.taskshare.app.data.model.lastCompletedAt
import com.taskshare.app.data.model.urgencyScore
import com.taskshare.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

enum class SortMode { URGENCY, ROOM, OWNER }

data class MainUiState(
    val groups: List<TaskGroup> = emptyList(),
    val sortMode: SortMode = SortMode.URGENCY,
    val localUserId: String? = null,
)

data class TaskGroup(val label: String, val tasks: List<TaskWithMeta>)

data class TaskWithMeta(val task: Task, val ownerNames: List<String>, val displayState: TaskDisplayState)

/** What a task card shows instead of (or alongside) the urgency bar. */
sealed interface TaskDisplayState {
    /** Hasn't reached its start date yet — not due, not actionable, just informational. */
    data class NotStarted(val startDate: LocalDate) : TaskDisplayState
    /** A one-time task that's been done — see the "stays visible, marked done" design decision. */
    data class Completed(val completedAt: Instant) : TaskDisplayState
    /** Normal due/overdue state: recurring, or a one-time task not yet done. */
    data class Due(val urgency: Double) : TaskDisplayState
}

/** Sort position for "most overdue first": actionable tasks by urgency, not-yet-started and
 *  already-done tasks pushed to the bottom (in that relative order) since neither needs attention. */
private val TaskDisplayState.sortKey: Double
    get() = when (this) {
        is TaskDisplayState.Due -> urgency
        is TaskDisplayState.Completed -> -1.0
        is TaskDisplayState.NotStarted -> -2.0
    }

class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    private val sortMode = MutableStateFlow(SortMode.URGENCY)

    val uiState: StateFlow<MainUiState> = combine(
        repository.observeActiveTasks(),
        repository.observeInstances(),
        repository.observeUsers(),
        sortMode,
    ) { tasks, instances, users, mode ->
        buildUiState(tasks, instances, users, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    fun setSortMode(mode: SortMode) {
        sortMode.value = mode
    }

    fun markDone(taskId: String, byUserId: String) {
        viewModelScope.launch { repository.recordCompletion(taskId, byUserId) }
    }

    /** "Remove tasks locally" — local-only, never propagates; see Task.archived doc. */
    fun removeTask(taskId: String) {
        viewModelScope.launch { repository.archiveTask(taskId) }
    }

    private fun buildUiState(
        tasks: List<Task>,
        instances: List<TaskInstance>,
        users: List<HouseholdUser>,
        mode: SortMode,
    ): MainUiState {
        val namesById = users.associate { it.id to it.displayName }
        val now = Instant.now()
        val withMeta = tasks.map { task ->
            val lastCompleted = task.lastCompletedAt(instances)
            val displayState = when {
                !task.hasStarted(now) -> TaskDisplayState.NotStarted(task.startDate)
                !task.isRepeating && lastCompleted != null -> TaskDisplayState.Completed(lastCompleted)
                else -> TaskDisplayState.Due(task.urgencyScore(instances, now))
            }
            TaskWithMeta(
                task = task,
                ownerNames = task.ownerIds.map { namesById[it] ?: "Unknown" },
                displayState = displayState,
            )
        }

        val groups = when (mode) {
            SortMode.URGENCY -> listOf(
                TaskGroup("Most overdue first", withMeta.sortedByDescending { it.displayState.sortKey })
            )
            SortMode.ROOM -> withMeta
                .groupBy { it.task.location.ifBlank { "Unspecified room" } }
                .toSortedMap()
                .map { (room, list) -> TaskGroup(room, list.sortedByDescending { it.displayState.sortKey }) }
            SortMode.OWNER -> withMeta
                .flatMap { meta -> if (meta.ownerNames.isEmpty()) listOf("Unassigned" to meta) else meta.ownerNames.map { it to meta } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
                .map { (owner, list) -> TaskGroup(owner, list.sortedByDescending { it.displayState.sortKey }) }
        }

        val localUserId = users.firstOrNull { it.isLocal }?.id
        return MainUiState(groups = groups, sortMode = mode, localUserId = localUserId)
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { MainViewModel(repository) }
        }
    }
}
