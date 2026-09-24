package com.taskshare.app.data.model

import java.time.Instant
import java.time.LocalDate

/**
 * A task definition — either recurring ([frequency] non-null) or a one-time task
 * ([frequency] null, done once and never due again).
 *
 * [startDate] anchors scheduling for every task, repeating or not: for a recurring task it's
 * when the first occurrence is due (frequency counts forward from there, not from [createdAt]);
 * for a one-time task it's simply the (only) due date. A task whose [startDate] hasn't arrived
 * yet shows on the main list labeled "Starts on [date]" rather than with a due/overdue state —
 * see MainViewModel.TaskDisplayState.
 *
 * Sync note: [name] is the cross-device matching key (see SyncMerger) — two devices that each
 * create a task called "Dishes" before ever syncing will merge into one task. Only the initial
 * creation of a task propagates between devices; later local edits to [location], [owners],
 * [priority], [frequency] or [startDate] stay device-local by design (see README "Sync model").
 * Renaming a task after it has already synced will NOT propagate and will break future
 * name-matching for that task on other devices — a known, documented limitation of the
 * additive/no-overwrite model.
 */
data class Task(
    val id: String,
    val name: String,
    val description: String,
    val location: String,
    val frequency: Frequency?,
    val startDate: LocalDate,
    val ownerIds: List<String>,
    val priority: Priority,
    val createdAt: Instant,
    /** Local-only: true once the user retires this task. Never leaves this device (see README). */
    val archived: Boolean = false,
) {
    companion object {
        /** Case/whitespace-insensitive form used everywhere tasks are matched across devices. */
        fun normalizeName(name: String): String = name.trim().lowercase()
    }

    val normalizedName: String get() = normalizeName(name)
    val isRepeating: Boolean get() = frequency != null
}
