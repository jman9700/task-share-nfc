package com.taskshare.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.data.sync.MergeResult
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.transport.DeviceTransport
import com.taskshare.app.update.RemoteAppVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** A peer's APK, already downloaded to [apkFile] and ready to install — see AvailableUpdate doc. */
data class AvailableUpdate(val peerVersion: RemoteAppVersion, val apkFile: File)

sealed interface ShareState {
    data object Idle : ShareState
    data object WaitingForTap : ShareState
    data object Syncing : ShareState
    data class Done(val result: MergeResult, val availableUpdate: AvailableUpdate?) : ShareState
    data class Error(val message: String) : ShareState
}

class ShareUpdateViewModel(
    private val repository: TaskRepository,
    private val nfcHandshake: NfcHandshake,
    private val transport: DeviceTransport,
    private val localVersionCode: Long,
    private val localVersionName: String,
    private val localApkSource: File,
) : ViewModel() {

    private val _state = MutableStateFlow<ShareState>(ShareState.Idle)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    /**
     * Downloading a peer's APK into our own cache isn't the risky step — installing it is, and
     * that's still gated by a separate user action (the update-ready dialog) plus Android's own
     * install-consent flow (see MainActivity.requestInstall). So there's no separate "confirm
     * before pulling" round trip here: if we're outdated relative to the peer, the bytes just
     * come along as part of the same connection used for the task sync, rather than requiring
     * the Bluetooth socket to stay open indefinitely while the user looks at a dialog.
     */
    fun startShare(apkDestination: File) {
        viewModelScope.launch {
            _state.value = ShareState.WaitingForTap
            try {
                val pairing = nfcHandshake.waitForTap()
                _state.value = ShareState.Syncing
                val session = transport.connect(pairing)
                try {
                    val peerVersion = session.exchangeVersion(localVersionCode, localVersionName)

                    val outgoing = repository.buildOutgoingPayload()
                    val incoming = session.exchangeSyncPayload(outgoing)
                    val mergeResult = repository.applyIncoming(incoming)

                    val downloadedApk = session.syncApkIfOutdated(
                        localVersionCode = localVersionCode,
                        peerVersionCode = peerVersion.versionCode,
                        isActiveSide = true,
                        localApkSource = localApkSource,
                        destination = apkDestination,
                    )

                    val availableUpdate = downloadedApk?.let { AvailableUpdate(peerVersion, it) }
                    _state.value = ShareState.Done(mergeResult, availableUpdate)
                } finally {
                    session.close()
                }
            } catch (e: Exception) {
                _state.value = ShareState.Error(e.message ?: "Sync failed")
            }
        }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            nfcHandshake: NfcHandshake,
            transport: DeviceTransport,
            localVersionCode: Long,
            localVersionName: String,
            localApkSource: File,
        ) = viewModelFactory {
            initializer {
                ShareUpdateViewModel(repository, nfcHandshake, transport, localVersionCode, localVersionName, localApkSource)
            }
        }
    }
}
