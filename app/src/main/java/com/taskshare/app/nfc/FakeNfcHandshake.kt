package com.taskshare.app.nfc

import com.taskshare.app.transport.PairingInfo

/** Used by E2E UI tests: "completes" a tap immediately with a canned pairing result. */
class FakeNfcHandshake(private val result: PairingInfo) : NfcHandshake {
    override suspend fun waitForTap(): PairingInfo = result
}
