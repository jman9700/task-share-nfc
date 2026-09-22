package com.taskshare.app.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.repository.TaskRepository
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(repository: TaskRepository, onBack: () -> Unit) {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                CalendarMode.entries.forEach { m ->
                    FilterChip(
                        selected = state.mode == m,
                        onClick = { viewModel.setMode(m) },
                        label = { Text(m.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                IconButton(onClick = { viewModel.step(forward = false) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                }
                Text(
                    state.rangeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                )
                IconButton(onClick = { viewModel.step(forward = true) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                }
                TextButton(onClick = viewModel::goToday, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Today")
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                state.entriesByDay.toSortedMap().forEach { (day, entries) ->
                    item {
                        Text(
                            day.format(dayFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(entries) { entry ->
                        val prefix = if (entry.type == CalendarEntryType.COMPLETED) "Done: " else "Due: "
                        Text(
                            prefix + entry.taskName,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                            color = if (entry.type == CalendarEntryType.COMPLETED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
