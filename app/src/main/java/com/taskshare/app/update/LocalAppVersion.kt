package com.taskshare.app.update

import android.content.Context
import android.os.Build
import java.io.File

/** This device's own installed version, in the same shape used to describe a peer's. */
object LocalAppVersion {
    fun get(context: Context): RemoteAppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return RemoteAppVersion(versionCode, info.versionName ?: "unknown")
    }

    /** This device's own installed APK, readable without any special permission. */
    fun apkFile(context: Context): File =
        File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir)
}
