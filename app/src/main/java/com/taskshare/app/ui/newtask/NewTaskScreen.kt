package com.taskshare.app.ui.newtask

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.model.Frequency
import com.taskshare.app.data.model.FrequencyUnit
import com.taskshare.app.data.model.Priority
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.ui.theme.TaskShareTopBar
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskScreen(repository: TaskRepository, onSaved: () -> Unit, onCancel: () -> Unit) {
    val viewModel: NewTaskViewModel = viewModel(factory = NewTaskViewModel.factory(repository))
    val users by viewModel.knownUsers.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var repeats by remember { mutableStateOf(true) }
    var quantityText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(FrequencyUnit.WEEK) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var selectedOwnerIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(topBar = { TaskShareTopBar(title = "New task", onBack = onCancel) }) { padding ->
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

            Text("Start date", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                "When this task is first due" + if (repeats) " — the frequency below counts forward from here." else ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = startDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                onValueChange = {},
                readOnly = true,
                label = { Text("Start date") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Change") } },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Repeats", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (repeats) "Recurs on a schedule" else "One-time task, done once",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = repeats, onCheckedChange = { repeats = it })
            }

            if (repeats) {
                Text("Done once every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter(Char::isDigit) },
                        label = { Text("x") },
                        modifier = Modifier
                            .width(80.dp)
                            .padding(end = 8.dp),
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
            }

            Text("Priority", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
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
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
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

            Button(
                enabled = name.isNotBlank() && (!repeats || quantityText.toIntOrNull()?.let { it > 0 } == true),
                onClick = {
                    viewModel.save(
                        name = name,
                        description = description,
                        location = location,
                        frequency = if (repeats) Frequency(quantityText.toInt(), unit) else null,
                        startDate = startDate,
                        ownerIds = selectedOwnerIds.toList(),
                        priority = priority,
                        onSaved = onSaved,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) { Text("Save task") }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        startDate = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
