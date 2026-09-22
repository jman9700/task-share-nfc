package com.taskshare.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.data.sync.MergeResult
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.transport.DeviceTransport
import com.taskshare.app.transport.TransportSession
import com.taskshare.app.update.AppVersionChecker
import com.taskshare.app.update.RemoteAppVersion
import com.taskshare.app.update.VersionComparison
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ShareState {
    data object Idle : ShareState
    data object WaitingForTap : ShareState
    data object Syncing : ShareState
    data class Done(val result: MergeResult, val newerPeerVersion: RemoteAppVersion?) : ShareState
    data class Error(val message: String) : ShareState
}

class ShareUpdateViewModel(
    private val repository: TaskRepository,
    private val nfcHandshake: NfcHandshake,
    private val transport: DeviceTransport,
    private val localVersionCode: Long,
    private val localVersionName: String,
) : ViewModel() {

    private val _state = MutableStateFlow<ShareState>(ShareState.Idle)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private var openSession: TransportSession? = null

    fun startShare() {
        viewModelScope.launch {
            _state.value = ShareState.WaitingForTap
            try {
                val pairing = nfcHandshake.waitForTap()
                _state.value = ShareState.Syncing
                val session = transport.connect(pairing)
                openSession = session

                val peerVersion = session.exchangeVersion(localVersionCode, localVersionName)
                val comparison = AppVersionChecker.compare(localVersionCode, peerVersion)

                val outgoing = repository.buildOutgoingPayload()
                val incoming = session.exchangeSyncPayload(outgoing)
                val mergeResult = repository.applyIncoming(incoming)

                val newerPeer = (comparison as? VersionComparison.PeerIsNewer)?.peer
                _state.value = ShareState.Done(mergeResult, newerPeer)
                if (newerPeer == null) {
                    session.close()
                    openSession = null
                }
            } catch (e: Exception) {
                _state.value = ShareState.Error(e.message ?: "Sync failed")
                openSession?.close()
                openSession = null
            }
        }
    }

    /** Called when the user answers the "pull the newer app version?" popup. */
    fun respondToVersionPrompt(accept: Boolean, destination: File, onApkReady: (File) -> Unit) {
        viewModelScope.launch {
            val session = openSession
            try {
                if (accept && session != null) {
                    val apk = session.pullApk(destination)
                    onApkReady(apk)
                }
            } finally {
                session?.close()
                openSession = null
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { openSession?.close() }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            nfcHandshake: NfcHandshake,
            transport: DeviceTransport,
            localVersionCode: Long,
            localVersionName: String,
        ) = viewModelFactory {
            initializer { ShareUpdateViewModel(repository, nfcHandshake, transport, localVersionCode, localVersionName) }
        }
    }
}
