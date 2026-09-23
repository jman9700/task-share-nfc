package com.taskshare.app.update

/** A version descriptor exchanged during a sync, used for both the local and peer device. */
data class RemoteAppVersion(val versionCode: Long, val versionName: String)
