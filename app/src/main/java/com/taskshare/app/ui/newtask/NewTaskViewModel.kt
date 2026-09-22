package com.taskshare.app.ui.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NewTaskViewModel(private val repository: TaskRepository) : ViewModel() {

    val knownUsers: StateFlow<List<HouseholdUser>> = repository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        name: String,
        description: String,
        location: String,
        frequency: Frequency,
        ownerIds: List<String>,
        priority: Priority,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.createTask(name, description, location, frequency, ownerIds, priority)
            onSaved()
        }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { NewTaskViewModel(repository) }
        }
    }
}
