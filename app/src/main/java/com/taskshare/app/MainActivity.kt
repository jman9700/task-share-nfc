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
import androidx.lifecycle.lifecycleScope
import com.taskshare.app.nfc.NfcHandshake
import com.taskshare.app.nfc.NfcReaderModeHandshake
import com.taskshare.app.testing.TaskShareTestHooks
import com.taskshare.app.transport.BluetoothDeviceTransport
import com.taskshare.app.transport.DeviceTransport
import com.taskshare.app.ui.navigation.TaskShareNavHost
import com.taskshare.app.ui.theme.TaskShareTheme
import com.taskshare.app.update.LocalAppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** FileProvider can only hand out URIs for paths under a root declared in
     *  res/xml/file_paths.xml — this is the one declared there (cache-path "transfer/"). */
    private fun transferDir(): File = File(cacheDir, "transfer").apply { mkdirs() }

    private fun newApkDestination(): File = File(transferDir(), "task-share-update.apk")

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
        lifecycleScope.launch(Dispatchers.IO) {
            // LocalAppVersion.apkFile() points into /data/app/..., which is NOT under any root
            // FileProvider is configured for (see file_paths.xml) — and can't be, since that's
            // the system's install directory, not app-private storage. Copy it into our own
            // cache dir first, same as the download path already does for an *incoming* APK,
            // then share that. Off the main thread since this is real file I/O.
            val shareableApk = File(transferDir(), "task-share-app.apk")
            LocalAppVersion.apkFile(this@MainActivity).copyTo(shareableApk, overwrite = true)

            val uri: Uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", shareableApk)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                startActivity(Intent.createChooser(intent, "Send Task Share app"))
            }
        }
    }
}
