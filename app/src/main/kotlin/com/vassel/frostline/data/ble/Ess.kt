// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import java.util.UUID

/** Environmental Sensing Service (GATT) constants and pure value parsers. */
object Ess {
    private fun uuid16(v: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", v))

    val SERVICE: UUID = uuid16(0x181A)
    val TEMPERATURE: UUID = uuid16(0x2A6E)
    val HUMIDITY: UUID = uuid16(0x2A6F)
    val CCCD: UUID = uuid16(0x2902)

    /** sint16 little-endian in 0.01 degC. 0x8000 means "unknown" and wrong lengths are invalid: both give null. */
    fun parseTemperature(bytes: ByteArray?): Double? {
        if (bytes == null || bytes.size != 2) return null
        val raw = ((bytes[0].toInt() and 0xFF) or (bytes[1].toInt() shl 8)).toShort()
        return if (raw == Short.MIN_VALUE) null else raw / 100.0
    }

    /** uint16 little-endian in 0.01 %. 0xFFFF means "unknown". */
    fun parseHumidity(bytes: ByteArray?): Double? {
        if (bytes == null || bytes.size != 2) return null
        val raw = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return if (raw == 0xFFFF) null else raw / 100.0
    }
}
