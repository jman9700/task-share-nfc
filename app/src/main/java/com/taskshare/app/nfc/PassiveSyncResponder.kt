package com.taskshare.app.nfc

import android.content.Context
import com.taskshare.app.TaskShareApp
import com.taskshare.app.permissions.BluetoothPermissions
import com.taskshare.app.transport.BluetoothRfcommServer
import com.taskshare.app.transport.ble.BlePairingAdvertiser
import com.taskshare.app.update.LocalAppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs on the phone that gets tapped — the passive side of a sync, driven entirely from
 * [NfcHandshakeHostService] rather than any screen the user is looking at. There is deliberately
 * no UI here: this device's user may not even have the app open. It mirrors what
 * ShareUpdateViewModel does on the active side (advertise so the peer can find us, accept the
 * RFCOMM connection, exchange versions, exchange and merge the sync payload) using the same
 * BluetoothTransportSession protocol code, since both ends of the exchange run identical
 * send-then-receive steps once the socket is open.
 *
 * Scope note: this does NOT yet serve APK bytes if the peer asks for a newer-version pull — that
 * needs a small protocol addition (a request flag after the payload exchange) that's still part
 * of the still-open "known gap" for APK distribution, not this fix. A peer that requests a pull
 * against this responder will just see its read fail once we close the socket.
 *
 * Permissions: since there's no Activity here, this can only use permissions already granted
 * beforehand — see BluetoothPermissionScreen, shown right after onboarding for exactly this
 * reason. If the user skipped it (or revoked it later), [BluetoothPermissions.allGranted] below
 * short-circuits before touching the radio at all, rather than relying solely on a caught
 * SecurityException from deeper in BlePairingAdvertiser/BluetoothRfcommServer.
 */
class PassiveSyncResponder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget: must return immediately since this is called from an APDU callback. */
    fun respondAsync(sessionToken: String) {
        if (!BluetoothPermissions.allGranted(context)) return

        scope.launch {
            val advertiser = BlePairingAdvertiser(context)
            try {
                advertiser.start(sessionToken)
                runSync()
            } catch (_: Exception) {
                // No UI to surface a failure to on this side; the active side's ShareUpdateScreen
                // will time out and show its own error.
            } finally {
                advertiser.stop()
            }
        }
    }

    private suspend fun runSync() {
        val app = context.applicationContext as TaskShareApp
        val session = BluetoothRfcommServer(context).acceptOnce()
        try {
            val local = LocalAppVersion.get(context)
            // Result intentionally unused: this side has no UI to offer a version-pull popup on.
            session.exchangeVersion(local.versionCode, local.versionName)

            val outgoing = app.repository.buildOutgoingPayload()
            val incoming = session.exchangeSyncPayload(outgoing)
            app.repository.applyIncoming(incoming)
        } finally {
            session.close()
        }
    }
}
