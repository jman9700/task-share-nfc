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
    /**
     * A random per-tap identifier — NOT a Bluetooth address. Android has blocked apps from
     * reading their own Bluetooth MAC since 6.0, so the NFC handshake can no longer hand over an
     * address directly. Instead this token is what the peer advertises over BLE right after the
     * tap; the connecting side discovers it via a BLE scan and reads the REAL address off the
     * scan result — that read is not restricted, only reading your own adapter's address is. See
     * transport/ble/BlePairingAdvertiser.kt and BlePairingScanner.kt.
     */
    val sessionToken: String,
)

interface TransportSession {
    /** Exchanges each side's local dataset. Both sides call this; it returns the PEER's payload. */
    suspend fun exchangeSyncPayload(outgoing: SyncPayload): SyncPayload

    /** Exchanges installed app version info, so either side can offer to pull a newer APK. */
    suspend fun exchangeVersion(localVersionCode: Long, localVersionName: String): RemoteAppVersion

    /**
     * Run once by both sides, right after [exchangeSyncPayload] — no separate user confirmation
     * gates this call itself, since fetching bytes into our own cache isn't the risky step;
     * actually installing them is, and that's still gated by a separate UI prompt plus Android's
     * own install-consent flow (see MainActivity.requestInstall). Only [isActiveSide] ever
     * receives, and only if it's also the older side — the passive/tapped side never receives
     * (it has no UI to ever offer an install prompt from), it only ever serves its own APK if
     * asked. Both sides pass their own [localVersionCode]/the peer's [peerVersionCode] (already
     * known from [exchangeVersion]) plus which role they are; each side derives the same
     * conclusion about whether bytes should move and in which direction without needing any
     * further back-and-forth beyond the one bit this method exchanges to agree on roles.
     *
     * Returns the downloaded file if this side received one, else null.
     */
    suspend fun syncApkIfOutdated(
        localVersionCode: Long,
        peerVersionCode: Long,
        isActiveSide: Boolean,
        localApkSource: File,
        destination: File,
    ): File?

    suspend fun close()
}
