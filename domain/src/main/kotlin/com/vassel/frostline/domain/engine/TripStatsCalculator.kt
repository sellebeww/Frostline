// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.TripStats

object TripStatsCalculator {
    /** Open excursions are measured up to [asOfUtcMs]. Excursions never overlap, so durations are summed. */
    fun compute(readings: List<Reading>, excursions: List<Excursion>, intervalSec: Int, asOfUtcMs: Long): TripStats {
        val gaps = Weighting.analyze(readings, intervalSec).gaps
        val durations = excursions.map { it.durationMs(asOfUtcMs) }
        return TripStats(
            readingCount = readings.size,
            minC = readings.minOfOrNull { it.tempC },
            maxC = readings.maxOfOrNull { it.tempC },
            meanC = MktCalculator.weightedMean(readings, intervalSec),
            mktC = MktCalculator.compute(readings, intervalSec),
            outOfRangeMs = durations.sum(),
            excursionCount = excursions.size,
            longestExcursionMs = durations.maxOrNull() ?: 0L,
            gapCount = gaps.size,
            gapTotalMs = gaps.sumOf { it.durationMs },
        )
    }
}
