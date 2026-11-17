// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlePermissionsTest {
    @Test
    fun androidTwelveAndUpUsesBluetoothPermissionsForScanAndConnect() {
        val scan = BlePermissions.forScan(34)
        assertTrue("android.permission.BLUETOOTH_SCAN" in scan && "android.permission.BLUETOOTH_CONNECT" in scan)
        assertFalse("android.permission.ACCESS_FINE_LOCATION" in scan)
        assertEquals(listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.POST_NOTIFICATIONS"), BlePermissions.forMonitoring(34))
    }

    @Test
    fun androidElevenAndBelowScansWithLocationAndNeedsNoRuntimeConnectPermission() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), BlePermissions.forScan(30))
        assertEquals(emptyList(), BlePermissions.forMonitoring(30))
    }

    @Test
    fun notificationPermissionStartsAtAndroidThirteen() {
        assertFalse("android.permission.POST_NOTIFICATIONS" in BlePermissions.forScan(32))
        assertTrue("android.permission.POST_NOTIFICATIONS" in BlePermissions.forScan(33))
        assertEquals(listOf("android.permission.BLUETOOTH_CONNECT"), BlePermissions.forMonitoring(32))
    }
}
