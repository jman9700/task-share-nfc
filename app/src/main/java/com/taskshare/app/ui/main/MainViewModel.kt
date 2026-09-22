package com.taskshare.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.model.TaskInstance
import com.taskshare.app.data.model.urgencyScore
import com.taskshare.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode { URGENCY, ROOM, OWNER }

data class MainUiState(
    val groups: List<TaskGroup> = emptyList(),
    val sortMode: SortMode = SortMode.URGENCY,
    val localUserId: String? = null,
)

data class TaskGroup(val label: String, val tasks: List<TaskWithMeta>)

data class TaskWithMeta(val task: Task, val urgency: Double, val ownerNames: List<String>)

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

    private fun buildUiState(
        tasks: List<Task>,
        instances: List<TaskInstance>,
        users: List<HouseholdUser>,
        mode: SortMode,
    ): MainUiState {
        val namesById = users.associate { it.id to it.displayName }
        val withMeta = tasks.map { task ->
            TaskWithMeta(
                task = task,
                urgency = task.urgencyScore(instances),
                ownerNames = task.ownerIds.map { namesById[it] ?: "Unknown" },
            )
        }

        val groups = when (mode) {
            SortMode.URGENCY -> listOf(
                TaskGroup("Most overdue first", withMeta.sortedByDescending { it.urgency })
            )
            SortMode.ROOM -> withMeta
                .groupBy { it.task.location.ifBlank { "Unspecified room" } }
                .toSortedMap()
                .map { (room, list) -> TaskGroup(room, list.sortedByDescending { it.urgency }) }
            SortMode.OWNER -> withMeta
                .flatMap { meta -> if (meta.ownerNames.isEmpty()) listOf("Unassigned" to meta) else meta.ownerNames.map { it to meta } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
                .map { (owner, list) -> TaskGroup(owner, list.sortedByDescending { it.urgency }) }
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
