package com.taskshare.app.permissions

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BluetoothPermissionsTest {

    @Test
    fun `below API 31 requires only fine location`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BluetoothPermissions.required(sdkInt = 30),
        )
    }

    @Test
    fun `API 31 and above requires the nearby-devices permission trio, not location`() {
        val result = BluetoothPermissions.required(sdkInt = 31)
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ),
            result,
        )
    }
}
