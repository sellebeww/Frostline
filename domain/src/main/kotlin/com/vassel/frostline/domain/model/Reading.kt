// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

/** One temperature sample. [tsUtcMs] is wall clock, [elapsedMs] is monotonic time since boot. */
data class Reading(
    val tsUtcMs: Long,
    val elapsedMs: Long,
    val tempC: Double,
    val humidityPct: Double? = null,
) {
    /** Temperature in 0.01 degC, the unit used for canonical hashing and threshold checks. */
    val tempCenti: Int get() = Math.round(tempC * 100.0).toInt()
}
