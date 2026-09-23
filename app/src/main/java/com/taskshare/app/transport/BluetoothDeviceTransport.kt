package com.taskshare.app.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.transport.ble.BlePairingScanner
import com.taskshare.app.update.RemoteAppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/**
 * Real Bluetooth Classic (RFCOMM) transport, used by the side that actively initiated the share
 * (pressed "Start NFC tap"). The NFC handshake no longer hands over a Bluetooth address directly
 * — Android blocks apps from reading their own adapter's address — so [connect] first resolves
 * the peer's real address via a BLE scan for the session token the peer is advertising (see
 * transport/ble/BlePairingScanner.kt), then opens the RFCOMM socket as before. The passive side
 * (the phone that was tapped) is served by [BluetoothRfcommServer] instead — see
 * nfc/PassiveSyncResponder.kt for how that's wired up from the NFC HCE service.
 *
 * NOT YET HARDWARE-TESTED. This needs a real two-device pass to validate: runtime permission
 * prompts (BLUETOOTH_CONNECT/BLUETOOTH_SCAN on API 31+, ACCESS_FINE_LOCATION below that),
 * pairing/bonding UX if the two phones have never paired before, socket reliability across OEM
 * Bluetooth stacks, and transfer time for a realistic APK size. See README "Known gaps".
 */
class BluetoothDeviceTransport(
    private val context: Context,
) : DeviceTransport {

    override suspend fun connect(pairingInfo: PairingInfo): TransportSession = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = manager?.adapter ?: error("No Bluetooth adapter on this device")

        val address = withTimeout(SCAN_TIMEOUT_MS) {
            BlePairingScanner(context).findPeerAddress(pairingInfo.sessionToken)
        }
        val device = adapter.getRemoteDevice(address)
        // TODO: this requires BLUETOOTH_CONNECT at call time (API 31+) and that the device is
        // either already bonded or bonds here; unbonded RFCOMM connect will prompt the user.
        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()
        BluetoothTransportSession(socket)
    }

    companion object {
        // Randomly generated, fixed UUID identifying this app's sync protocol over RFCOMM.
        val SERVICE_UUID: UUID = UUID.fromString("7b1e6f2e-6b34-4f0a-9b7a-2f6b8f6a2b41")

        /** How long to wait for the peer's BLE advertisement before giving up. */
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}

/**
 * Wraps a connected RFCOMM socket and speaks the sync protocol. Shared by both sides of a sync:
 * the client obtains one from [BluetoothDeviceTransport.connect], the passive/accepting side
 * obtains one from [BluetoothRfcommServer.acceptOnce]. Each method here writes its own side of
 * the exchange first and then reads the peer's — since both sides run the exact same sequence of
 * calls in the same order, this works without either side needing to know it's the "client" or
 * "server" once the socket is open.
 */
internal class BluetoothTransportSession(private val socket: BluetoothSocket) : TransportSession {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)

    override suspend fun exchangeSyncPayload(outgoing: SyncPayload): SyncPayload = withContext(Dispatchers.IO) {
        writeFrame(SyncPayloadCodec.encode(outgoing))
        val bytes = readFrame()
        SyncPayloadCodec.decode(bytes)
    }

    override suspend fun exchangeVersion(localVersionCode: Long, localVersionName: String): RemoteAppVersion =
        withContext(Dispatchers.IO) {
            output.writeLong(localVersionCode)
            output.writeUTF(localVersionName)
            output.flush()
            RemoteAppVersion(input.readLong(), input.readUTF())
        }

    override suspend fun pullApk(destination: File): File = withContext(Dispatchers.IO) {
        val size = input.readLong()
        destination.outputStream().use { out ->
            val buffer = ByteArray(8192)
            var remaining = size
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        destination
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket.close()
    }

    private fun writeFrame(bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readFrame(): ByteArray {
        val size = input.readInt()
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return bytes
    }
}
