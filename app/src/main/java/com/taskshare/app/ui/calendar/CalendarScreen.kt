package com.taskshare.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.ui.theme.TaskShareTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(repository: TaskRepository, onBack: () -> Unit) {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TaskShareTopBar(title = "Calendar", onBack = onBack) },
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.step(forward = false) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous")
                }
                Text(
                    state.rangeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
                IconButton(onClick = { viewModel.step(forward = true) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next")
                }
                TextButton(onClick = viewModel::goToday) { Text("Today") }
            }

            when (state.mode) {
                CalendarMode.DAY -> DayView(date = state.selectedDate, entriesByDay = state.entriesByDay)
                CalendarMode.WEEK -> WeekView(weekDates = state.weekDates, entriesByDay = state.entriesByDay)
                CalendarMode.MONTH -> MonthView(
                    grid = state.monthGrid,
                    entriesByDay = state.entriesByDay,
                    selectedDate = state.selectedDate,
                    onSelectDay = viewModel::selectDay,
                )
            }
        }
    }
}

@Composable
private fun DayView(date: LocalDate, entriesByDay: Map<LocalDate, List<CalendarEntry>>) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                date.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        val entries = entriesByDay[date].orEmpty()
        if (entries.isEmpty()) {
            item { NoTasksLabel() }
        } else {
            items(entries) { AgendaRow(it) }
        }
    }
}

@Composable
private fun WeekView(weekDates: List<LocalDate>, entriesByDay: Map<LocalDate, List<CalendarEntry>>) {
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val today = LocalDate.now()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        weekDates.forEach { date ->
            item {
                Text(
                    date.format(dayFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            val entries = entriesByDay[date].orEmpty()
            if (entries.isEmpty()) {
                item { NoTasksLabel() }
            } else {
                items(entries) { AgendaRow(it) }
            }
        }
    }
}

@Composable
private fun MonthView(
    grid: List<MonthGridDay>,
    entriesByDay: Map<LocalDate, List<CalendarEntry>>,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val weeks = grid.chunked(7)
        items(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                week.forEach { cell ->
                    MonthGridCell(
                        cell = cell,
                        isToday = cell.date == today,
                        isSelected = cell.date == selectedDate,
                        hasEntries = entriesByDay[cell.date]?.isNotEmpty() == true,
                        onClick = { onSelectDay(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        val entries = entriesByDay[selectedDate].orEmpty()
        if (entries.isEmpty()) {
            item { NoTasksLabel() }
        } else {
            items(entries) { AgendaRow(it) }
        }
    }
}

@Composable
private fun MonthGridCell(
    cell: MonthGridDay,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntries: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !cell.inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (hasEntries) {
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary
                    } else {
                        Color.Transparent
                    }
                ),
        )
    }
}

@Composable
private fun AgendaRow(entry: CalendarEntry) {
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

@Composable
private fun NoTasksLabel() {
    Text(
        "No tasks",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}
