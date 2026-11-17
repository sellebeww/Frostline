// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.model.Reading
import kotlin.math.exp
import kotlin.math.ln

/**
 * Mean kinetic temperature with delta H = 83144 J/mol and R = 8.3144 J/(mol K). Each reading is weighted by the seconds
 * until the next reading; the last reading has no successor and intervals above the gap threshold get no weight.
 * If no interval carries weight (a single reading, or only gaps) all readings are weighted equally instead.
 */
object MktCalculator {
    private const val DELTA_H = 83144.0
    private const val R = 8.3144
    private const val KELVIN = 273.15

    fun compute(readings: List<Reading>, intervalSec: Int): Double? {
        if (readings.isEmpty()) return null
        val segments = weighted(readings, intervalSec)
        var sum = 0.0
        var total = 0.0
        for ((t, w) in segments) {
            sum += w * exp(-DELTA_H / (R * (t + KELVIN)))
            total += w
        }
        val denominator = -ln(sum / total)
        return (DELTA_H / R) / denominator - KELVIN
    }

    /** Duration-weighted arithmetic mean, with the same weighting and fallback as [compute]. */
    fun weightedMean(readings: List<Reading>, intervalSec: Int): Double? {
        if (readings.isEmpty()) return null
        val segments = weighted(readings, intervalSec)
        return segments.sumOf { it.weightSec * it.tempC } / segments.sumOf { it.weightSec }
    }

    private fun weighted(readings: List<Reading>, intervalSec: Int): List<Weighting.Segment> {
        val segments = Weighting.analyze(readings, intervalSec).segments.filter { it.weightSec > 0.0 }
        return segments.ifEmpty { readings.map { Weighting.Segment(it.tempC, 1.0) } }
    }
}
