package com.taskshare.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.taskshare.app.transport.PairingInfo
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Host Card Emulation service: this is what makes the device answer an NFC tap FROM the other
 * phone's reader-mode scan (see NfcReaderModeHandshake). It responds to a SELECT AID command by
 * returning this device's [PairingInfo] as a single APDU response.
 *
 * KNOWN GAP — needs on-device validation before this ships: as of Android 6.0,
 * BluetoothAdapter.getAddress() no longer returns the device's real MAC address for privacy
 * reasons (it returns a constant placeholder) unless called with a special system permission
 * this app won't have. So "bluetoothAddress" below cannot actually come from getAddress().
 * Two realistic fixes to evaluate on real hardware: (a) have this device start Bluetooth
 * Classic discoverability/a server socket and rely on the OS-level pairing flow instead of a
 * literal MAC string, or (b) switch the payload transport from Bluetooth Classic to BLE and
 * exchange a BLE-advertised identifier instead of a MAC. Left as a TODO rather than papered
 * over with a fake address, since this is a real platform constraint, not a bug in this code.
 */
class NfcHandshakeHostService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !isSelectAid(commandApdu)) return STATUS_UNKNOWN

        val pairing = PairingInfo(
            peerDeviceId = LocalDeviceIdentity.deviceId(applicationContext),
            bluetoothAddress = TODO_PLACEHOLDER_ADDRESS,
            sessionToken = UUID.randomUUID().toString(),
        )
        val body = "${pairing.peerDeviceId}|${pairing.bluetoothAddress}|${pairing.sessionToken}"
            .toByteArray(StandardCharsets.UTF_8)
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
        // See the class doc above: this must be replaced before real device transfer works.
        private const val TODO_PLACEHOLDER_ADDRESS = "00:00:00:00:00:00"
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
