package com.taskshare.app.transport

/** Shared logcat tag for the whole NFC/BLE/Bluetooth sync path — `adb logcat -s TaskShareSync`
 *  pulls every step from both the active (tapping) and passive (tapped) sides. */
object TaskShareSyncLog {
    const val TAG = "TaskShareSync"
}
