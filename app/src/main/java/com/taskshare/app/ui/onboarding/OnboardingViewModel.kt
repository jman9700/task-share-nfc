package com.taskshare.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.model.HouseholdUser
import com.taskshare.app.data.repository.TaskRepository
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingViewModel(private val repository: TaskRepository) : ViewModel() {

    fun createLocalUser(displayName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addLocalUser(HouseholdUser(id = UUID.randomUUID().toString(), displayName = displayName.trim(), isLocal = true))
            onDone()
        }
    }

    companion object {
        fun factory(repository: TaskRepository) = viewModelFactory {
            initializer { OnboardingViewModel(repository) }
        }
    }
}
