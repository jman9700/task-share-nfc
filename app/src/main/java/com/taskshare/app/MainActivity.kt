package com.taskshare.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.nfc.NfcReaderModeHandshake
import com.taskshare.app.testing.TaskShareTestHooks
import com.taskshare.app.transport.BluetoothDeviceTransport
import com.taskshare.app.transport.DeviceTransport
import com.taskshare.app.ui.navigation.TaskShareNavHost
import com.taskshare.app.ui.theme.TaskShareTheme
import com.taskshare.app.update.LocalAppVersion
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TaskShareApp
        val nfcHandshake: NfcHandshake = TaskShareTestHooks.nfcHandshakeOverride ?: NfcReaderModeHandshake(this)
        val transport: DeviceTransport = TaskShareTestHooks.transportOverride ?: BluetoothDeviceTransport(this)
        val localVersion = LocalAppVersion.get(this)

        setContent {
            TaskShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskShareNavHost(
                        repository = app.repository,
                        nfcHandshake = nfcHandshake,
                        transport = transport,
                        localVersionCode = localVersion.versionCode,
                        localVersionName = localVersion.versionName,
                        localApkSource = LocalAppVersion.apkFile(this),
                        apkDownloadDestination = { newApkDestination() },
                        onInstallApk = { file -> requestInstall(file) },
                        onShareApkExternally = { shareApkExternally() },
                    )
                }
            }
        }
    }

    private fun newApkDestination(): File {
        val dir = File(cacheDir, "transfer").apply { mkdirs() }
        return File(dir, "task-share-update.apk")
    }

    private fun requestInstall(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * Handles onboarding a phone that has NEVER installed Task Share, which our NFC handshake
     * fundamentally cannot do — it needs the app already running on both ends (Android removed
     * phone-to-phone NDEF push in Android 10). Instead this hands the APK to the standard Android
     * share sheet, where the person picks a transport themselves — usually Nearby Share, which is
     * a Play Services feature the *receiving* phone already has regardless of whether it has ever
     * heard of Task Share, so it can still catch the file. No server/hosting involved, consistent
     * with the rest of this app.
     */
    private fun shareApkExternally() {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", LocalAppVersion.apkFile(this))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send Task Share app"))
    }
}
