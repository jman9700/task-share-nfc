package com.taskshare.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as TaskShareApp
        val nfcHandshake: NfcHandshake = TaskShareTestHooks.nfcHandshakeOverride ?: NfcReaderModeHandshake(this)
        val transport: DeviceTransport = TaskShareTestHooks.transportOverride ?: BluetoothDeviceTransport(this)
        val versionCode = packageManager.getPackageInfo(packageName, 0).let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        }
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

        setContent {
            TaskShareTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskShareNavHost(
                        repository = app.repository,
                        nfcHandshake = nfcHandshake,
                        transport = transport,
                        localVersionCode = versionCode,
                        localVersionName = versionName,
                        apkDownloadDestination = { newApkDestination() },
                        onInstallApk = { file -> requestInstall(file) },
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
}
