package com.taskshare.app.update

/** Minimal version descriptor exchanged during the NFC handshake, before any bulk transfer. */
data class RemoteAppVersion(val versionCode: Long, val versionName: String)

sealed interface VersionComparison {
    /** The peer is on the same or an older version; nothing to offer. */
    data object UpToDate : VersionComparison
    /** The peer has a newer build — the UI should prompt the user before pulling it. */
    data class PeerIsNewer(val peer: RemoteAppVersion) : VersionComparison
}

object AppVersionChecker {
    /** versionCode is the sole source of truth for "newer" — versionName is for display only. */
    fun compare(localVersionCode: Long, peer: RemoteAppVersion): VersionComparison =
        if (peer.versionCode > localVersionCode) VersionComparison.PeerIsNewer(peer) else VersionComparison.UpToDate
}
