package com.taskshare.app.testing

import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.transport.DeviceTransport

/**
 * Test seam for the standalone :tests module. Real E2E tests can't tap two physical phones
 * together, so instrumented tests launch the app's actual Activities/screens but set these
 * overrides beforehand to substitute FakeNfcHandshake / FakeDeviceTransport for the real radio
 * implementations. MainActivity checks these before constructing the real (untested-on-hardware)
 * NfcReaderModeHandshake / BluetoothDeviceTransport. Left null in production.
 */
object TaskShareTestHooks {
    var nfcHandshakeOverride: NfcHandshake? = null
    var transportOverride: DeviceTransport? = null

    fun reset() {
        nfcHandshakeOverride = null
        transportOverride = null
    }
}
