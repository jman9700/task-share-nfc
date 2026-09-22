package com.taskshare.app.transport

import com.taskshare.app.data.sync.SyncPayload
import com.taskshare.app.update.RemoteAppVersion
import java.io.File

/**
 * In-process loopback transport used by E2E UI tests (see the :tests module) to drive the
 * real share/sync screens without Bluetooth or NFC hardware. Configured with a canned "peer"
 * payload/version the test wants the device under test to receive.
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

        override suspend fun pullApk(destination: File): File {
            destination.writeBytes(fakeApkBytes)
            return destination
        }

        override suspend fun close() = Unit
    }
}
