// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.r
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripStatsCalculatorTest {
    @Test
    fun emptyInputHasNoValues() {
        val s = TripStatsCalculator.compute(emptyList(), emptyList(), 60, 0L)
        assertEquals(0, s.readingCount)
        assertNull(s.minC); assertNull(s.maxC); assertNull(s.meanC); assertNull(s.mktC)
        assertEquals(0L, s.outOfRangeMs)
    }

    @Test
    fun aggregatesMinMaxMeanMktExcursionsAndGaps() {
        val readings = listOf(r(0, 4.0), r(60, 6.0), r(120, 9.0), r(180, 9.0), r(240, 5.0), r(2000, 5.0), r(2060, 5.0))
        val ex = listOf(
            Excursion("T", ExcursionType.HEAT, 120_000L, 240_000L, 9.0),
            Excursion("T", ExcursionType.COLD, 2_000_000L, null, 1.0),
        )
        val s = TripStatsCalculator.compute(readings, ex, 60, asOfUtcMs = 2_100_000L)
        assertEquals(7, s.readingCount)
        assertEquals(4.0, s.minC)
        assertEquals(9.0, s.maxC)
        // weights: 4C 60, 6C 60, 9C 60, 9C 60, 5C gap (1760 s) excluded, 5C 60 => (240+360+540+540+300)/300
        assertEquals((4.0 * 60 + 6.0 * 60 + 9.0 * 120 + 5.0 * 60) / 300.0, s.meanC!!, 1e-9)
        assertEquals(2, s.excursionCount)
        assertEquals(120_000L + 100_000L, s.outOfRangeMs)
        assertEquals(120_000L, s.longestExcursionMs)
        assertEquals(1, s.gapCount)
        assertEquals(1_760_000L, s.gapTotalMs)
        assert(s.mktC!! >= s.meanC)
    }
}
