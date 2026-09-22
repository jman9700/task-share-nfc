package com.taskshare.app.nfc

import com.taskshare.app.transport.PairingInfo

/**
 * NFC's job in this app is narrow and deliberate: a tap exchanges a small handshake payload
 * (peer device id, its Bluetooth MAC address, a one-time session token) and nothing else.
 * Classic NDEF push/Android Beam is deprecated since Android 10 and NFC's raw bandwidth is far
 * too low for a task database or an APK, so bulk transfer always happens afterwards over
 * Bluetooth (see transport/DeviceTransport.kt). This interface is faked in E2E tests so the
 * share/sync screen can be driven without two physical phones tapped together.
 */
interface NfcHandshake {
    /** Suspends until a tap completes, or throws if the user cancels / it times out. */
    suspend fun waitForTap(): PairingInfo
}
