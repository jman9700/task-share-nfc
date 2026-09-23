package com.taskshare.app.transport.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scans for the BLE advertisement a peer started via [BlePairingAdvertiser] right after an NFC
 * tap, and resolves it to that peer's real Bluetooth address — reading the address off a
 * *discovered* device is unrestricted; it's only reading your own adapter's address that Android
 * blocks. Callers should wrap this in `withTimeout(...)`; it has no timeout of its own and will
 * otherwise wait until cancelled.
 */
class BlePairingScanner(private val context: Context) {

    suspend fun findPeerAddress(sessionToken: String): String = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            continuation.resumeWithException(IllegalStateException("BLE scanning unavailable on this device"))
            return@suspendCancellableCoroutine
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(sessionToken))).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (continuation.isActive) {
                    continuation.resume(result.device.address)
                    stopSafely(scanner, this)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("BLE scan failed, error $errorCode"))
                }
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            // BLUETOOTH_SCAN (API 31+) or ACCESS_FINE_LOCATION (API 26-30) not granted.
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation { stopSafely(scanner, callback) }
    }

    private fun stopSafely(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback) {
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            // Already lost permission mid-scan; nothing more we can do.
        }
    }
}
