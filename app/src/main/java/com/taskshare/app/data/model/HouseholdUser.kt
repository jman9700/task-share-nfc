package com.taskshare.app.data.model

/**
 * A person sharing this task list. Identity is a locally-generated UUID, not tied to any
 * account or server; devices exchange their HouseholdUser record during NFC/Bluetooth sync
 * so the other side can attribute ownership and completions by name instead of raw UUID.
 */
data class HouseholdUser(
    val id: String,
    val displayName: String,
    /** True for the identity that "owns" this device/install. */
    val isLocal: Boolean = false,
)
