// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.model.GapInfo
import com.vassel.frostline.domain.model.Reading

/** Duration weighting shared by MKT, the weighted mean and gap detection. */
internal object Weighting {
    /** Intervals longer than max(3 x log interval, 300 s) are gaps. */
    fun gapThresholdMs(intervalSec: Int): Long = maxOf(3L * intervalSec, 300L) * 1000L

    /** A reading and the seconds it stays valid, until the next reading. */
    data class Segment(val tempC: Double, val weightSec: Double)

    data class Result(val segments: List<Segment>, val gaps: List<GapInfo>)

    fun analyze(readings: List<Reading>, intervalSec: Int): Result {
        val sorted = readings.sortedWith(compareBy({ it.tsUtcMs }, { it.elapsedMs }))
        val limit = gapThresholdMs(intervalSec)
        val segments = ArrayList<Segment>()
        val gaps = ArrayList<GapInfo>()
        for (i in 0 until sorted.size - 1) {
            val dt = sorted[i + 1].tsUtcMs - sorted[i].tsUtcMs
            if (dt > limit) {
                gaps += GapInfo(sorted[i].tsUtcMs, sorted[i + 1].tsUtcMs)
            } else {
                segments += Segment(sorted[i].tempC, dt / 1000.0)
            }
        }
        return Result(segments, gaps)
    }
}
