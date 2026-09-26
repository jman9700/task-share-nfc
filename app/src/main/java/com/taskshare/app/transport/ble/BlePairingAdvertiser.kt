package com.taskshare.app.transport.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.taskshare.app.transport.TaskShareSyncLog
import java.util.UUID

/**
 * Advertises a per-tap [sessionToken] over BLE so the peer that just tapped us can find this
 * device and read our real Bluetooth address off the scan result (see BlePairingScanner). The
 * token itself becomes the advertised service UUID — a 128-bit UUID is exactly the shape BLE
 * advertisements already support carrying, so no custom payload parsing is needed.
 *
 * Runs from a background context (the HCE host service has no Activity to request permissions
 * from), so a missing BLUETOOTH_ADVERTISE grant surfaces as a caught SecurityException, not a
 * crash — the connecting side will simply time out. See README "Known gaps" (permissions).
 *
 * Every early-return here is logged at WARN under [TaskShareSyncLog.TAG]: `bluetoothLeAdvertiser`
 * returns null both when this device doesn't support BLE peripheral mode AND when Bluetooth is
 * simply turned off, which otherwise fails completely silently — the connecting side just times
 * out with no clue why. If a real sync fails, `adb logcat -s TaskShareSync` on the phone that was
 * tapped (not the one that pressed "Start NFC tap") is the first thing to check.
 */
class BlePairingAdvertiser(private val context: Context) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    fun start(sessionToken: String) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (manager == null) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: no BluetoothManager on this device")
            return
        }
        val adapter = manager.adapter
        if (adapter == null) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: no BluetoothAdapter on this device")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: Bluetooth is turned off on this device")
            return
        }
        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: bluetoothLeAdvertiser unavailable (no BLE peripheral support?)")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(ADVERTISE_WINDOW_MS)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID.fromString(sessionToken)))
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TaskShareSyncLog.TAG, "BlePairingAdvertiser: advertising started for token $sessionToken")
            }

            override fun onStartFailure(errorCode: Int) {
                // Common causes: ADVERTISE_FAILED_ALREADY_STARTED (1), _TOO_MANY_ADVERTISERS (2),
                // _FEATURE_UNSUPPORTED (5) — the last means this chipset can't do BLE advertising
                // at all, a real hardware ceiling this architecture would need to work around.
                Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: onStartFailure errorCode=$errorCode")
            }
        }

        try {
            leAdvertiser.startAdvertising(settings, data, cb)
            advertiser = leAdvertiser
            callback = cb
        } catch (e: SecurityException) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingAdvertiser: BLUETOOTH_ADVERTISE not granted", e)
        }
    }

    fun stop() {
        val cb = callback ?: return
        try {
            advertiser?.stopAdvertising(cb)
        } catch (_: SecurityException) {
            // Nothing to clean up if we never got permission to start in the first place.
        }
        advertiser = null
        callback = null
    }

    companion object {
        /** BLE's own hardware timeout caps this at 180s; we don't need to wait that long. */
        const val ADVERTISE_WINDOW_MS = 30_000
    }
}
