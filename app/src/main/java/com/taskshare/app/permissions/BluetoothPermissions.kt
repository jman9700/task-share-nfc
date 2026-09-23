package com.taskshare.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime ("dangerous") permissions needed for the BLE-discovery + Bluetooth Classic sync
 * path (see transport/ble/BlePairingAdvertiser.kt, BlePairingScanner.kt). NFC itself needs no
 * runtime permission — it's a normal, install-time one.
 *
 * The permission set is API-level dependent, so [required] takes the SDK level as a parameter
 * (defaulting to the real device's) purely so the branching logic is a plain function that can
 * be unit tested without Robolectric — see BluetoothPermissionsTest.
 */
object BluetoothPermissions {

    fun required(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = if (sdkInt >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        // Below API 31, BLE scanning/advertising piggybacks on the location permission — an
        // Android platform quirk (see AndroidManifest.xml's comment on ACCESS_FINE_LOCATION).
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun allGranted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Thrown by the transport layer when a sync can't proceed because a permission is missing. */
class MissingBluetoothPermissionException(message: String) : Exception(message)
