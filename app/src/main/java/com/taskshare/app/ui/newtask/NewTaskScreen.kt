package com.taskshare.app.ui.newtask

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.repository.TaskRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(repository: TaskRepository, onSaved: () -> Unit, onCancel: () -> Unit) {
    val viewModel: NewTaskViewModel = viewModel(factory = NewTaskViewModel.factory(repository))
    val users by viewModel.knownUsers.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(FrequencyUnit.WEEK) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var selectedOwnerIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(topBar = { TopAppBar(title = { Text("New task") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                description, { description = it }, label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                location, { location = it }, label = { Text("Location / room") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Text("Done once every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = { Text("x") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FrequencyUnit.entries.forEach { u ->
                    FilterChip(
                        selected = unit == u,
                        onClick = { unit = u },
                        label = { Text(u.name.lowercase()) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            Text("Priority", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.name.lowercase()) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            Text("Owners", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.padding(top = 4.dp)) {
                users.forEach { user ->
                    FilterChip(
                        selected = user.id in selectedOwnerIds,
                        onClick = {
                            selectedOwnerIds = if (user.id in selectedOwnerIds) {
                                selectedOwnerIds - user.id
                            } else {
                                selectedOwnerIds + user.id
                            }
                        },
                        label = { Text(user.displayName) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
                Button(
                    enabled = name.isNotBlank() && quantityText.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        viewModel.save(
                            name = name,
                            description = description,
                            location = location,
                            frequency = Frequency(quantityText.toInt(), unit),
                            ownerIds = selectedOwnerIds.toList(),
                            priority = priority,
                            onSaved = onSaved,
                        )
                    },
                ) { Text("Save task") }
            }
        }
    }
}
