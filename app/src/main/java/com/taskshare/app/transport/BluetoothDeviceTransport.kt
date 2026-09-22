package com.taskshare.app.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.update.RemoteAppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/**
 * Real Bluetooth Classic (RFCOMM) transport. Bulk data (the JSON sync payload, and the APK on a
 * version pull) goes over a socket opened to [PairingInfo.bluetoothAddress], which the NFC
 * handshake supplied.
 *
 * NOT YET HARDWARE-TESTED. This needs a real two-device pass to validate: runtime permission
 * prompts (BLUETOOTH_CONNECT/BLUETOOTH_SCAN on API 31+), pairing/bonding UX if the two phones
 * have never paired before, socket reliability across OEM Bluetooth stacks, and transfer time
 * for a realistic APK size. Treat this class as a structural skeleton, not a finished feature —
 * see README "Known gaps" before relying on it.
 */
class BluetoothDeviceTransport(
    private val context: Context,
) : DeviceTransport {

    companion object {
        // Randomly generated, fixed UUID identifying this app's sync protocol over RFCOMM.
        val SERVICE_UUID: UUID = UUID.fromString("7b1e6f2e-6b34-4f0a-9b7a-2f6b8f6a2b41")
    }

    override suspend fun connect(pairingInfo: PairingInfo): TransportSession = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: error("No Bluetooth adapter on this device")
        val device = adapter.getRemoteDevice(pairingInfo.bluetoothAddress)
        // TODO: this requires BLUETOOTH_CONNECT at call time (API 31+) and that the device is
        // either already bonded or bonds here; unbonded RFCOMM connect will prompt the user.
        val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()
        BluetoothTransportSession(socket)
    }
}

private class BluetoothTransportSession(private val socket: BluetoothSocket) : TransportSession {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)

    override suspend fun exchangeSyncPayload(outgoing: SyncPayload): SyncPayload = withContext(Dispatchers.IO) {
        // TODO: replace with a real serializer (kotlinx.serialization) once the wire format is
        // finalized; this class only defines the transport shape for now.
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

/** Placeholder wire codec — swap for kotlinx.serialization JSON before this ships. */
private object SyncPayloadCodec {
    fun encode(payload: SyncPayload): ByteArray = TODO("Serialize SyncPayload, e.g. with kotlinx.serialization")
    fun decode(bytes: ByteArray): SyncPayload = TODO("Deserialize SyncPayload")
}
