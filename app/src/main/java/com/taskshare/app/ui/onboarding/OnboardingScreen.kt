package com.taskshare.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.repository.TaskRepository

@Composable
fun OnboardingScreen(repository: TaskRepository, onDone: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(repository))
    var name by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Welcome to Task Share", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This app has no server — your task list lives on this phone and syncs with your " +
                    "partner's phone over NFC + Bluetooth. What should we call you?",
                modifier = Modifier.padding(vertical = 16.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
            Button(
                onClick = { viewModel.createLocalUser(name) { onDone() } },
                enabled = name.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Get started")
            }
        }
    }
}
