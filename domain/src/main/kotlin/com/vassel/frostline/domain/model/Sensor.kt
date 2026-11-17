// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

import java.security.MessageDigest

/** A paired BLE sensor. [address] is private (stored only in the encrypted database); [macHash] may be shown and exported. */
data class Sensor(val id: String, val macHash: String, val model: String?, val address: String)

object MacHash {
    /**
     * First 12 hex characters of SHA-256 over the upper-case address. It identifies a sensor on screen and in reports
     * without printing the MAC. It is not anonymisation: a 48-bit address can be brute-forced from it.
     */
    fun of(address: String): String =
        MessageDigest.getInstance("SHA-256").digest(address.trim().uppercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(12)
}
