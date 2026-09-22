package com.taskshare.app.transport

import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.update.RemoteAppVersion
import java.io.File

/**
 * Abstraction over "send bulk bytes to the paired peer and get bytes back". The real
 * implementation (Bluetooth Classic RFCOMM, chosen for better throughput on APK-sized transfers
 * than BLE's small MTU) lives behind this interface specifically so E2E UI tests can substitute
 * an in-process fake and drive the full share-and-sync screen without two physical radios.
 *
 * A connection is established using the pairing info produced by the NFC handshake
 * (see nfc/NfcHandshake.kt) — this interface only covers what happens after that handshake.
 */
interface DeviceTransport {
    suspend fun connect(pairingInfo: PairingInfo): TransportSession
}

data class PairingInfo(
    val peerDeviceId: String,
    /** Bluetooth MAC address of the peer, obtained via the NFC handshake payload. */
    val bluetoothAddress: String,
    val sessionToken: String,
)

interface TransportSession {
    /** Exchanges each side's local dataset. Both sides call this; it returns the PEER's payload. */
    suspend fun exchangeSyncPayload(outgoing: SyncPayload): SyncPayload

    /** Exchanges installed app version info, so either side can offer to pull a newer APK. */
    suspend fun exchangeVersion(localVersionCode: Long, localVersionName: String): RemoteAppVersion

    /** Requests the peer's APK bytes and saves them locally; only called after user confirms the popup. */
    suspend fun pullApk(destination: File): File

    suspend fun close()
}
