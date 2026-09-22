package com.taskshare.app.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.repository.TaskRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: TaskRepository,
    onOpenCalendar: () -> Unit,
    onAddTask: () -> Unit,
    onShareUpdate: () -> Unit,
) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Share") },
                actions = {
                    IconButton(onClick = onShareUpdate) {
                        Icon(Icons.Filled.Nfc, contentDescription = "Share update via NFC")
                    }
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendar view")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SortModeRow(current = state.sortMode, onSelect = viewModel::setSortMode)
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                state.groups.forEach { group ->
                    item { GroupHeader(group.label) }
                    items(group.tasks, key = { it.task.id }) { meta ->
                        TaskRow(
                            task = meta.task,
                            urgency = meta.urgency,
                            ownerNames = meta.ownerNames,
                            onMarkDone = { state.localUserId?.let { viewModel.markDone(meta.task.id, it) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortModeRow(current: SortMode, onSelect: (SortMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Sort by", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.Row(modifier = Modifier.padding(top = 4.dp)) {
            FilterChip(
                selected = current == SortMode.URGENCY,
                onClick = { onSelect(SortMode.URGENCY) },
                label = { Text("Most overdue") },
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            FilterChip(
                selected = current == SortMode.ROOM,
                onClick = { onSelect(SortMode.ROOM) },
                label = { Text("Room") },
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            FilterChip(
                selected = current == SortMode.OWNER,
                onClick = { onSelect(SortMode.OWNER) },
                label = { Text("Owner") },
            )
        }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TaskRow(task: Task, urgency: Double, ownerNames: List<String>, onMarkDone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(task.name, style = MaterialTheme.typography.titleMedium)
            Text("${task.location} · ${task.frequency.label()}", style = MaterialTheme.typography.bodySmall)
            if (ownerNames.isNotEmpty()) {
                Text(ownerNames.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = if (urgency >= 1.0) "Overdue" else "Due in ${(1 - urgency).let { "%.0f%%".format(it * 100) }} of cycle",
                style = MaterialTheme.typography.bodySmall,
                color = if (urgency >= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(onClick = onMarkDone) {
                Text("Mark done")
            }
        }
    }
}
