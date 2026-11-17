// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EssTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun uuidsAreTheBluetoothBaseUuids() {
        assertEquals("0000181a-0000-1000-8000-00805f9b34fb", Ess.SERVICE.toString())
        assertEquals("00002a6e-0000-1000-8000-00805f9b34fb", Ess.TEMPERATURE.toString())
        assertEquals("00002a6f-0000-1000-8000-00805f9b34fb", Ess.HUMIDITY.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", Ess.CCCD.toString())
    }

    @Test
    fun positiveTemperatureIsLittleEndianCentiDegrees() {
        assertEquals(25.0, Ess.parseTemperature(b(0xC4, 0x09))) // 0x09C4 = 2500
        assertEquals(5.23, Ess.parseTemperature(b(0x0B, 0x02))) // 0x020B = 523
        assertEquals(0.0, Ess.parseTemperature(b(0x00, 0x00)))
        assertEquals(327.67, Ess.parseTemperature(b(0xFF, 0x7F)))
    }

    @Test
    fun negativeTemperatureIsSigned() {
        assertEquals(-5.0, Ess.parseTemperature(b(0x0C, 0xFE))) // 0xFE0C = -500
        assertEquals(-0.01, Ess.parseTemperature(b(0xFF, 0xFF)))
        assertEquals(-327.67, Ess.parseTemperature(b(0x01, 0x80)))
    }

    @Test
    fun unknownValueAndWrongLengthsAreIgnored() {
        assertNull(Ess.parseTemperature(b(0x00, 0x80))) // 0x8000 = unknown
        assertNull(Ess.parseTemperature(null))
        assertNull(Ess.parseTemperature(b()))
        assertNull(Ess.parseTemperature(b(0x0B)))
        assertNull(Ess.parseTemperature(b(0x0B, 0x02, 0x00)))
    }

    @Test
    fun humidityIsUnsignedWithFfffUnknown() {
        assertEquals(55.0, Ess.parseHumidity(b(0x7C, 0x15))) // 0x157C = 5500
        assertEquals(100.0, Ess.parseHumidity(b(0x10, 0x27)))
        assertNull(Ess.parseHumidity(b(0xFF, 0xFF)))
        assertNull(Ess.parseHumidity(b(0x01)))
        assertNull(Ess.parseHumidity(null))
    }
}
