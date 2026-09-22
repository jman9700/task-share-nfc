package com.taskshare.app.data.model

import java.time.Instant

/**
 * A single completed occurrence of a task. [id] is generated at creation time (on whichever
 * device recorded the completion) and is the dedupe key during sync, so re-syncing the same
 * two devices repeatedly never creates duplicate completion records.
 */
data class TaskInstance(
    val id: String,
    val taskId: String,
    val completedAt: Instant,
    val completedByUserId: String,
)
