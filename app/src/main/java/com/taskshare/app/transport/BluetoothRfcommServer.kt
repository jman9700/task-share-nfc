package com.taskshare.app.transport

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Accept side of the RFCOMM connection, used by the phone that was tapped (see
 * nfc/PassiveSyncResponder.kt) rather than the one that pressed "Start NFC tap". Listens on the
 * same well-known service UUID [BluetoothDeviceTransport] connects to, and hands back the same
 * [BluetoothTransportSession] wrapper the client side uses, so both ends of a sync run identical
 * protocol code once the socket is open.
 */
class BluetoothRfcommServer(private val context: Context) {

    suspend fun acceptOnce(timeoutMs: Long = 30_000): TransportSession = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: error("No Bluetooth adapter on this device")

        // TODO: requires BLUETOOTH_CONNECT (API 31+); see README "Known gaps" (permissions).
        val serverSocket: BluetoothServerSocket =
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, BluetoothDeviceTransport.SERVICE_UUID)
        try {
            val socket = withTimeout(timeoutMs) { serverSocket.accept() }
            BluetoothTransportSession(socket)
        } finally {
            serverSocket.close()
        }
    }

    companion object {
        private const val SERVICE_NAME = "TaskShareSync"
    }
}
