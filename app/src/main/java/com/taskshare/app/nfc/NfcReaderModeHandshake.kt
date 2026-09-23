package com.taskshare.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.taskshare.app.transport.PairingInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reader side of the NFC handshake: enables NFC reader mode while the share screen is open, and
 * on tap, talks to the peer's [NfcHandshakeHostService] to pull its [PairingInfo].
 *
 * NOT YET HARDWARE-TESTED — see BluetoothDeviceTransport for the remaining known gaps (runtime
 * permission prompts, bonding UX) that block a real end-to-end tap from working today.
 * Structurally wired so the share screen can call a real implementation as those get closed,
 * without changing the UI layer.
 */
class NfcReaderModeHandshake(private val activity: Activity) : NfcHandshake {

    override suspend fun waitForTap(): PairingInfo = suspendCancellableCoroutine { continuation ->
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            continuation.resumeWithException(IllegalStateException("No NFC adapter on this device"))
            return@suspendCancellableCoroutine
        }

        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            try {
                continuation.resume(readPairingInfo(tag))
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(t)
            } finally {
                adapter.disableReaderMode(activity)
            }
        }

        adapter.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )

        continuation.invokeOnCancellation { adapter.disableReaderMode(activity) }
    }

    private fun readPairingInfo(tag: Tag): PairingInfo {
        val isoDep = IsoDep.get(tag) ?: error("Tapped tag does not support ISO-DEP")
        isoDep.connect()
        try {
            val selectAid = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, TASK_SHARE_AID.size.toByte()) + TASK_SHARE_AID
            val response = isoDep.transceive(selectAid)
            require(response.size > 2) { "Empty response from peer" }
            val statusBytes = response.copyOfRange(response.size - 2, response.size)
            require(statusBytes.contentEquals(SW_OK)) { "Peer returned error status" }

            val body = String(response.copyOfRange(0, response.size - 2), StandardCharsets.UTF_8)
            val (deviceId, sessionToken) = body.split("|", limit = 2)
            return PairingInfo(deviceId, sessionToken)
        } finally {
            isoDep.close()
        }
    }
}
