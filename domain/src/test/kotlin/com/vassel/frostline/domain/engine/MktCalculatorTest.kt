// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.r
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MktCalculatorTest {
    @Test
    fun constant5CGivesFive() {
        val readings = (0..20).map { r(it * 60L, 5.0) }
        assertEquals(5.0, assertNotNull(MktCalculator.compute(readings, 60)), 0.01)
    }

    @Test
    fun mktIsNeverBelowWeightedMeanRandomised() {
        val rnd = Random(42)
        repeat(250) {
            val n = rnd.nextInt(2, 40)
            var t = 0L
            val readings = (0 until n).map {
                t += rnd.nextLong(10, 120)
                r(t, rnd.nextDouble(-10.0, 45.0))
            }
            val mkt = assertNotNull(MktCalculator.compute(readings, 60))
            val mean = assertNotNull(MktCalculator.weightedMean(readings, 60))
            assertTrue(mkt >= mean - 1e-9, "mkt=$mkt < mean=$mean")
        }
    }

    // Manual case: 2 degC for 60 s then 8 degC for 60 s, last reading (5 degC) has no weight.
    // dH/R = 83144 / 8.3144 = 10000 K. S = (exp(-10000/275.15) + exp(-10000/281.15)) / 2.
    // MKT_K = 10000 / -ln(S) = 278.686 K, so 5.5364 degC (arithmetic mean is 5.0).
    @Test
    fun mixedCaseMatchesHandCalculation() {
        val readings = listOf(r(0, 2.0), r(60, 8.0), r(120, 5.0))
        assertEquals(5.5364, assertNotNull(MktCalculator.compute(readings, 60)), 0.001)
    }

    @Test
    fun emptyAndSingleReadingAreSafe() {
        assertNull(MktCalculator.compute(emptyList(), 60))
        assertNull(MktCalculator.weightedMean(emptyList(), 60))
        assertEquals(7.25, assertNotNull(MktCalculator.compute(listOf(r(0, 7.25)), 60)), 1e-9)
        assertEquals(7.25, assertNotNull(MktCalculator.weightedMean(listOf(r(0, 7.25)), 60)), 1e-9)
    }

    // Reading at t=60 is 40 degC but the next reading is 3600 s later (> gap threshold 300 s), so it gets no weight.
    @Test
    fun gapIntervalIsExcludedFromWeighting() {
        val readings = listOf(r(0, 5.0), r(60, 40.0), r(3660, 5.0), r(3720, 5.0))
        assertEquals(5.0, assertNotNull(MktCalculator.compute(readings, 60)), 0.01)
        val gaps = Weighting.analyze(readings, 60).gaps
        assertEquals(1, gaps.size)
        assertEquals(60_000L, gaps[0].startUtcMs)
        assertEquals(3_660_000L, gaps[0].endUtcMs)
    }

    @Test
    fun gapThresholdIsMaxOfThreeIntervalsAndFiveMinutes() {
        assertEquals(300_000L, Weighting.gapThresholdMs(60))
        assertEquals(300_000L, Weighting.gapThresholdMs(100))
        assertEquals(540_000L, Weighting.gapThresholdMs(180))
    }

    @Test
    fun intervalExactlyAtThresholdIsStillWeighted() {
        val readings = listOf(r(0, 5.0), r(300, 5.0))
        assertEquals(0, Weighting.analyze(readings, 60).gaps.size)
    }
}
