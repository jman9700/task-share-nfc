package com.taskshare.app.transport.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.taskshare.app.transport.TaskShareSyncLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scans for the BLE advertisement a peer started via [BlePairingAdvertiser] right after an NFC
 * tap, and resolves it to that peer's real Bluetooth address — reading the address off a
 * *discovered* device is unrestricted; it's only reading your own adapter's address that Android
 * blocks. Callers should wrap this in `withTimeout(...)`; it has no timeout of its own and will
 * otherwise wait until cancelled — a timeout here almost always means the *other* phone's
 * BlePairingAdvertiser never actually started (see its class doc for why that fails silently on
 * that end); check that phone's logcat first, not this one's.
 */
class BlePairingScanner(private val context: Context) {

    suspend fun findPeerAddress(sessionToken: String): String = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter != null && !adapter.isEnabled) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingScanner: Bluetooth is turned off on this device")
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TaskShareSyncLog.TAG, "BlePairingScanner: bluetoothLeScanner unavailable on this device")
            continuation.resumeWithException(IllegalStateException("BLE scanning unavailable on this device"))
            return@suspendCancellableCoroutine
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(sessionToken))).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (continuation.isActive) {
                    Log.d(TaskShareSyncLog.TAG, "BlePairingScanner: found peer at ${result.device.address}")
                    continuation.resume(result.device.address)
                    stopSafely(scanner, this)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TaskShareSyncLog.TAG, "BlePairingScanner: onScanFailed errorCode=$errorCode")
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("BLE scan failed, error $errorCode"))
                }
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, callback)
            Log.d(TaskShareSyncLog.TAG, "BlePairingScanner: scan started for token $sessionToken")
        } catch (e: SecurityException) {
            // BLUETOOTH_SCAN (API 31+) or ACCESS_FINE_LOCATION (API 26-30) not granted.
            Log.w(TaskShareSyncLog.TAG, "BlePairingScanner: scan permission not granted", e)
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            Log.w(TaskShareSyncLog.TAG, "BlePairingScanner: scan cancelled/timed out without finding peer")
            stopSafely(scanner, callback)
        }
    }

    private fun stopSafely(scanner: android.bluetooth.le.BluetoothLeScanner, callback: ScanCallback) {
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            // Already lost permission mid-scan; nothing more we can do.
        }
    }
}
