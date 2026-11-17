// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

enum class ExcursionType(val severity: Int) {
    HEAT(1),
    COLD(1),
    FREEZE(2),
}

/** A temperature excursion. [endUtcMs] is null while open. [peakC] is the extreme value (max for HEAT, min otherwise). */
data class Excursion(
    val tripId: String,
    val type: ExcursionType,
    val startUtcMs: Long,
    val endUtcMs: Long?,
    val peakC: Double,
) {
    val severity: Int get() = type.severity
    val isOpen: Boolean get() = endUtcMs == null

    /** Deterministic id, unique per trip because only one excursion is open at a time. */
    val id: String get() = "$tripId:$startUtcMs"

    fun durationMs(asOfUtcMs: Long): Long = (endUtcMs ?: asOfUtcMs) - startUtcMs
}
