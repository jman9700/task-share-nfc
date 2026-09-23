package com.taskshare.app.transport

import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.update.RemoteAppVersion
import java.io.File

/**
 * In-process loopback transport used by E2E UI tests (see the :tests module) to drive the
 * real share/sync screens without Bluetooth or NFC hardware. Configured with a canned "peer"
 * payload/version the test wants the device under test to receive. [fakeApkBytes] is only
 * written to the destination if the caller identifies as the active side and the peer version
 * is newer than the local one passed in — mirroring the real transport's rule so tests can
 * exercise the update-available path realistically.
 */
class FakeDeviceTransport(
    private val peerPayload: SyncPayload,
    private val peerVersion: RemoteAppVersion,
    private val fakeApkBytes: ByteArray = ByteArray(0),
) : DeviceTransport {

    var lastSentPayload: SyncPayload? = null
        private set

    override suspend fun connect(pairingInfo: PairingInfo): TransportSession = object : TransportSession {
        override suspend fun exchangeSyncPayload(outgoing: SyncPayload): SyncPayload {
            lastSentPayload = outgoing
            return peerPayload
        }

        override suspend fun exchangeVersion(localVersionCode: Long, localVersionName: String): RemoteAppVersion =
            peerVersion

        override suspend fun syncApkIfOutdated(
            localVersionCode: Long,
            peerVersionCode: Long,
            isActiveSide: Boolean,
            localApkSource: File,
            destination: File,
        ): File? {
            if (!isActiveSide || localVersionCode >= peerVersionCode) return null
            destination.writeBytes(fakeApkBytes)
            return destination
        }

        override suspend fun close() = Unit
    }
}
