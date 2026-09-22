package com.taskshare.app.data.model

import java.time.Instant

/**
 * A recurring task definition.
 *
 * Sync note: [name] is the cross-device matching key (see SyncMerger) — two devices that each
 * create a task called "Dishes" before ever syncing will merge into one task. Only the initial
 * creation of a task propagates between devices; later local edits to [location], [owners],
 * [priority] or [frequency] stay device-local by design (see README "Sync model"). Renaming a
 * task after it has already synced will NOT propagate and will break future name-matching for
 * that task on other devices — a known, documented limitation of the additive/no-overwrite model.
 */
data class Task(
    val id: String,
    val name: String,
    val description: String,
    val location: String,
    val frequency: Frequency,
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
}
