package com.taskshare.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.model.Task
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.ui.theme.StatusBadge
import java.time.format.DateTimeFormatter

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
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
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SortModeRow(current = state.sortMode, onSelect = viewModel::setSortMode)
            if (state.groups.all { it.tasks.isEmpty() }) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    state.groups.forEach { group ->
                        if (group.tasks.isEmpty()) return@forEach
                        item { GroupHeader(group.label) }
                        items(group.tasks, key = { it.task.id }) { meta ->
                            DismissibleTaskRow(
                                meta = meta,
                                onMarkDone = { state.localUserId?.let { viewModel.markDone(meta.task.id, it) } },
                                onRemove = { viewModel.removeTask(meta.task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortModeRow(current: SortMode, onSelect: (SortMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Sort by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = current == SortMode.URGENCY,
                onClick = { onSelect(SortMode.URGENCY) },
                label = { Text("Most overdue") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = current == SortMode.ROOM,
                onClick = { onSelect(SortMode.ROOM) },
                label = { Text("Room") },
            )
            Spacer(modifier = Modifier.width(8.dp))
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "No tasks yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            "Tap the + button to add your first recurring chore.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Swipe (end-to-start) reveals a delete affordance; completing the swipe just opens a
 *  confirmation dialog rather than committing immediately — removal is still local-only and
 *  reversible only by re-syncing, so it's worth one extra tap to avoid an accidental swipe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleTaskRow(meta: TaskWithMeta, onMarkDone: () -> Unit, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showConfirm = true
            false // never let the box itself commit the dismiss; the dialog does
        },
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove task?") },
            text = {
                Text(
                    "\"${meta.task.name}\" will be removed from this device only. If your " +
                        "partner's phone still has it, it may reappear the next time you sync.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onRemove() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove task",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 28.dp),
                )
            }
        },
    ) {
        TaskRow(task = meta.task, displayState = meta.displayState, ownerNames = meta.ownerNames, onMarkDone = onMarkDone)
    }
}

@Composable
private fun TaskRow(task: Task, displayState: TaskDisplayState, ownerNames: List<String>, onMarkDone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoomIcon(task.location)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${task.location} · ${task.frequency?.label() ?: "One-time"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ownerNames.isNotEmpty()) {
                    Text(
                        ownerNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                when (displayState) {
                    is TaskDisplayState.Due -> UrgencyIndicator(displayState.urgency)
                    is TaskDisplayState.NotStarted -> Text(
                        "STARTS ${displayState.startDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
                        style = StatusBadge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is TaskDisplayState.Completed -> Text(
                        "COMPLETED",
                        style = StatusBadge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            when (displayState) {
                is TaskDisplayState.Due -> FilledIconButton(
                    onClick = onMarkDone,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Mark ${task.name} done")
                }
                is TaskDisplayState.Completed -> Icon(
                    Icons.Filled.Check,
                    contentDescription = "${task.name} completed",
                    tint = MaterialTheme.colorScheme.primary,
                )
                is TaskDisplayState.NotStarted -> Unit
            }
        }
    }
}

@Composable
private fun RoomIcon(location: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            iconForLocation(location),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun UrgencyIndicator(urgency: Double) {
    val progress = urgency.toFloat().coerceIn(0f, 1f)
    val overdue = urgency >= 1.0
    val color = when {
        overdue -> MaterialTheme.colorScheme.error
        urgency >= 0.75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Column {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = if (overdue) "OVERDUE" else "${(100 - progress * 100).let { "%.0f".format(it) }}% OF CYCLE LEFT",
            style = StatusBadge,
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun iconForLocation(location: String): ImageVector {
    val normalized = location.lowercase()
    return when {
        "kitchen" in normalized -> Icons.Filled.Kitchen
        "bath" in normalized -> Icons.Filled.Bathtub
        "bed" in normalized -> Icons.Filled.Bed
        "living" in normalized || "lounge" in normalized -> Icons.Filled.Weekend
        "garage" in normalized || "car" in normalized -> Icons.Filled.DirectionsCar
        "yard" in normalized || "garden" in normalized || "outdoor" in normalized -> Icons.Filled.Yard
        "office" in normalized || "study" in normalized -> Icons.Filled.Chair
        else -> Icons.Filled.Home
    }
}
