package com.taskshare.app.transport.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
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
 */
class BlePairingAdvertiser(private val context: Context) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    fun start(sessionToken: String) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val leAdvertiser = manager.adapter?.bluetoothLeAdvertiser ?: return

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
            override fun onStartFailure(errorCode: Int) {
                // Best-effort: nothing to surface to a user from a background service.
            }
        }

        try {
            leAdvertiser.startAdvertising(settings, data, cb)
            advertiser = leAdvertiser
            callback = cb
        } catch (_: SecurityException) {
            // BLUETOOTH_ADVERTISE not granted; see class doc.
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
