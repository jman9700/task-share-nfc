package com.taskshare.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionCheckerTest {

    @Test
    fun `peer with higher versionCode is reported as newer`() {
        val result = AppVersionChecker.compare(5L, RemoteAppVersion(6L, "1.1"))
        assertTrue(result is VersionComparison.PeerIsNewer)
    }

    @Test
    fun `peer with equal versionCode is up to date`() {
        assertEquals(VersionComparison.UpToDate, AppVersionChecker.compare(5L, RemoteAppVersion(5L, "1.0")))
    }

    @Test
    fun `peer with lower versionCode is up to date`() {
        assertEquals(VersionComparison.UpToDate, AppVersionChecker.compare(5L, RemoteAppVersion(4L, "0.9")))
    }
}
