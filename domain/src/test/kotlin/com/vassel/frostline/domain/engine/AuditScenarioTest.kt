// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.FakeClock
import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.r
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Phase 8 audit: expected values were computed by hand and cross-checked with an independent Python script that shares
 * no code with the project, not read back from the implementation.
 */
class AuditScenarioTest {
    // Readings every 60 s: 4, 6, 9, 5, 3 degC. The last reading has no successor, so the weights are 60 s each for
    // 4, 6, 9 and 5 degC. Weighted mean = 6.0. MKT = 10000 / -ln(mean of exp(-10000 / T_i)) - 273.15 = 6.220956 degC.
    @Test
    fun mktOfFiveReadingsMatchesAnIndependentCalculation() {
        val readings = listOf(r(0, 4.0), r(60, 6.0), r(120, 9.0), r(180, 5.0), r(240, 3.0))
        assertEquals(6.220956, assertNotNull(MktCalculator.compute(readings, 60)), 1e-5)
        assertEquals(6.0, assertNotNull(MktCalculator.weightedMean(readings, 60)), 1e-9)
    }

    // Same readings, then a 400 s silence (gap threshold is max(3 x 60, 300) = 300 s) and two readings of 5 degC.
    // The 240 s to 640 s interval gets no weight. Segments: 4, 6, 9, 5 and 5 degC at 60 s each:
    // mean = 5.8, MKT = 5.990815 degC.
    @Test
    fun aGapIsExcludedFromTheWeighting() {
        val readings = listOf(r(0, 4.0), r(60, 6.0), r(120, 9.0), r(180, 5.0), r(240, 3.0), r(640, 5.0), r(700, 5.0))
        assertEquals(5.990815, assertNotNull(MktCalculator.compute(readings, 60)), 1e-5)
        assertEquals(5.8, assertNotNull(MktCalculator.weightedMean(readings, 60)), 1e-9)
    }

    // Defaults 2.0 / 8.0 / 0.0 degC, hysteresis 0.2 degC, so "back inside" means 2.2 to 7.8 degC.
    //   t=60 8.5 and t=120 9.1: two hot readings open HEAT, start = 60 s, peak 9.1.
    //   t=180 7.9: inside the range but not inside the band, so it neither extends nor closes.
    //   t=240 7.7 and t=300 7.0: two readings back inside the band close HEAT, end = 240 s.
    //   t=360 1.5 and t=420 1.9: open COLD, start = 360 s, peak 1.5.
    //   t=480 -0.5: freezing, the SAME record becomes FREEZE (peak -0.5), FreezeRisk is raised.
    //   t=540 2.1: below the band, resets the return count.
    //   t=600 2.3 and t=660 5.0: close FREEZE, end = 600 s.
    // Time outside the range: (240 - 60) + (600 - 360) = 420 s.
    @Test
    fun heatThenColdEscalatingToFreezeMatchesAHandTrace() {
        val engine = ExcursionEngine("T", ThresholdProfile(), 60, FakeClock(), startUtcMs = 0L)
        val temps = listOf(5.0, 8.5, 9.1, 7.9, 7.7, 7.0, 1.5, 1.9, -0.5, 2.1, 2.3, 5.0)
        val events = temps.withIndex().flatMap { (i, c) -> engine.onReading(r(i * 60L, c)) }

        assertEquals(
            listOf("Opened:HEAT", "Closed:HEAT", "Opened:COLD", "Freeze:FREEZE", "Closed:FREEZE"),
            events.map {
                when (it) {
                    is AlarmEvent.ExcursionOpened -> "Opened:${it.excursion.type}"
                    is AlarmEvent.ExcursionClosed -> "Closed:${it.excursion.type}"
                    is AlarmEvent.FreezeRisk -> "Freeze:${it.excursion.type}"
                    else -> "other"
                }
            },
        )
        val (heat, freeze) = engine.excursions()
        assertEquals(ExcursionType.HEAT, heat.type)
        assertEquals(60_000L to 240_000L, heat.startUtcMs to heat.endUtcMs)
        assertEquals(9.1, heat.peakC, 1e-9)
        assertEquals(ExcursionType.FREEZE, freeze.type)
        assertEquals(360_000L to 600_000L, freeze.startUtcMs to freeze.endUtcMs)
        assertEquals(-0.5, freeze.peakC, 1e-9)
        assertEquals(420_000L, (heat.endUtcMs!! - heat.startUtcMs) + (freeze.endUtcMs!! - freeze.startUtcMs))
    }
}
