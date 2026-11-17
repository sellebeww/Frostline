// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.location

import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationResult

object LocationPolicy {
    const val CURRENT_TIMEOUT_MS = 10_000L
    const val LAST_MAX_AGE_MS = 120_000L

    /** A fresh fix wins. A cached one is accepted only when younger than 2 minutes and is flagged approximate. */
    fun choose(current: GeoLocation?, last: GeoLocation?, nowUtcMs: Long, timedOut: Boolean): LocationResult = when {
        current != null -> LocationResult.Fix(current)
        last != null && nowUtcMs - last.tsUtcMs in 0 until LAST_MAX_AGE_MS -> LocationResult.Fix(last, approximate = true)
        else -> LocationResult.None(if (timedOut) LocationFailure.TIMEOUT else LocationFailure.UNAVAILABLE)
    }
}
