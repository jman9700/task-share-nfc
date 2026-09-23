package com.taskshare.app.ui.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskshare.app.data.repository.TaskRepository
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.permissions.BluetoothPermissions
import com.taskshare.app.transport.DeviceTransport
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareUpdateScreen(
    repository: TaskRepository,
    nfcHandshake: NfcHandshake,
    transport: DeviceTransport,
    localVersionCode: Long,
    localVersionName: String,
    localApkSource: File,
    apkDownloadDestination: () -> File,
    onInstallApk: (File) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ShareUpdateViewModel = viewModel(
        factory = ShareUpdateViewModel.factory(
            repository, nfcHandshake, transport, localVersionCode, localVersionName, localApkSource,
        )
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(BluetoothPermissions.allGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasBluetoothPermission = results.values.all { it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share update") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasBluetoothPermission) {
                Text(
                    "Task Share needs Bluetooth permission to sync — NFC only handles the initial " +
                        "handshake, the actual task list moves over Bluetooth.",
                )
                Button(
                    onClick = { permissionLauncher.launch(BluetoothPermissions.required()) },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("Grant Bluetooth permission")
                }
                return@Column
            }

            when (val s = state) {
                is ShareState.Idle -> {
                    Text(
                        "Hold your phone near your partner's phone. New tasks and completions from " +
                            "each device get added to the other — nothing already on either phone is changed.",
                    )
                    Button(
                        onClick = { viewModel.startShare(apkDownloadDestination()) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Start NFC tap")
                    }
                }
                is ShareState.WaitingForTap -> {
                    CircularProgressIndicator()
                    Text("Waiting for tap…", modifier = Modifier.padding(top = 16.dp))
                }
                is ShareState.Syncing -> {
                    CircularProgressIndicator()
                    Text("Syncing…", modifier = Modifier.padding(top = 16.dp))
                }
                is ShareState.Done -> {
                    Text("Sync complete", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${s.result.newTasks.size} new task(s), ${s.result.newInstances.size} new completion(s), " +
                            "${s.result.newUsers.size} new person(s) added.",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (s.result.unresolvedInstances.isNotEmpty()) {
                        Text(
                            "${s.result.unresolvedInstances.size} completion(s) referenced a task not found here (likely archived locally).",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    var updateDialogDismissed by remember(s.availableUpdate) { mutableStateOf(false) }
                    if (s.availableUpdate != null && !updateDialogDismissed) {
                        UpdateReadyDialog(
                            peerVersionName = s.availableUpdate.peerVersion.versionName,
                            onInstall = {
                                onInstallApk(s.availableUpdate.apkFile)
                                updateDialogDismissed = true
                            },
                            onDecline = { updateDialogDismissed = true },
                        )
                    }
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Done") }
                }
                is ShareState.Error -> {
                    Text("Sync failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { viewModel.startShare(apkDownloadDestination()) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateReadyDialog(peerVersionName: String, onInstall: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Update ready") },
        text = { Text("Your partner's phone is on Task Share $peerVersionName. It's been downloaded — install it now?") },
        confirmButton = { TextButton(onClick = onInstall) { Text("Install now") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Not now") } },
    )
}
