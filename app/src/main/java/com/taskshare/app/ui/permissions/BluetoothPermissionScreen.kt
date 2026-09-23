package com.taskshare.app.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taskshare.app.permissions.BluetoothPermissions

private enum class PermissionState { NOT_REQUESTED, DENIED, GRANTED }

/**
 * Shown once, right after onboarding, since the sync feature needs Bluetooth-related runtime
 * permissions granted upfront: the phone that gets TAPPED runs PassiveSyncResponder from a
 * background NFC service with no Activity available to request permissions from, so waiting
 * until the user opens the share screen isn't enough to reliably serve an incoming tap. Declining
 * here doesn't block the rest of the app — task tracking still works, only sync won't.
 */
@Composable
fun BluetoothPermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf(if (BluetoothPermissions.allGranted(context)) PermissionState.GRANTED else PermissionState.NOT_REQUESTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        state = if (results.values.all { it }) PermissionState.GRANTED else PermissionState.DENIED
    }

    if (state == PermissionState.GRANTED) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Bluetooth needed to sync", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tapping phones uses NFC to say hello, then Bluetooth to actually move your task " +
                    "list. Android requires permission for that — this only leaves your phone, " +
                    "it's never sent anywhere else.",
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )

            when (state) {
                PermissionState.NOT_REQUESTED -> {
                    Button(onClick = { launcher.launch(BluetoothPermissions.required()) }) {
                        Text("Grant Bluetooth permission")
                    }
                }
                PermissionState.DENIED -> {
                    Text(
                        "Without it, syncing with your partner's phone won't work, but the rest of " +
                            "the app still will. You can grant this later from the Share screen.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Row {
                        TextButton(onClick = onDone) { Text("Skip for now") }
                        val activity = context as? Activity
                        if (activity != null && isPermanentlyDenied(activity)) {
                            TextButton(onClick = { openAppSettings(context) }) { Text("Open Settings") }
                        } else {
                            Button(onClick = { launcher.launch(BluetoothPermissions.required()) }) { Text("Try again") }
                        }
                    }
                }
                PermissionState.GRANTED -> Unit // handled above
            }
        }
    }
}

private fun isPermanentlyDenied(activity: Activity): Boolean =
    BluetoothPermissions.required().any { !activity.shouldShowRequestPermissionRationale(it) }

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}
