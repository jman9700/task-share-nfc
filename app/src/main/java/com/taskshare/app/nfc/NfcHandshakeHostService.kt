package com.taskshare.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.taskshare.app.transport.PairingInfo
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Host Card Emulation service: this is what makes the device answer an NFC tap FROM the other
 * phone's reader-mode scan (see NfcReaderModeHandshake). It responds to a SELECT AID command by
 * returning this device's [PairingInfo] as a single APDU response, then kicks off
 * [PassiveSyncResponder] to actually run the sync — processCommandApdu itself must return in a
 * few hundred milliseconds (NFC transactions are latency-sensitive), so it can't block on
 * Bluetooth setup; it only generates the session token and hands the rest off asynchronously.
 */
class NfcHandshakeHostService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !isSelectAid(commandApdu)) return STATUS_UNKNOWN

        val pairing = PairingInfo(
            peerDeviceId = LocalDeviceIdentity.deviceId(applicationContext),
            sessionToken = UUID.randomUUID().toString(),
        )
        PassiveSyncResponder(applicationContext).respondAsync(pairing.sessionToken)

        val body = "${pairing.peerDeviceId}|${pairing.sessionToken}".toByteArray(StandardCharsets.UTF_8)
        return body + SW_OK
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun isSelectAid(apdu: ByteArray): Boolean {
        // SELECT AID: 00 A4 04 00 <len> <AID bytes>
        if (apdu.size < 6) return false
        val header = apdu.copyOfRange(0, 4)
        return header.contentEquals(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00))
    }

    companion object {
        private val STATUS_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00)
    }
}

internal object LocalDeviceIdentity {
    private const val PREFS_NAME = "task_share_identity"
    private const val KEY_DEVICE_ID = "device_id"

    fun deviceId(context: android.content.Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }
}
