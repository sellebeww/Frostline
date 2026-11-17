// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.FakeClock
import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.r
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExcursionEngineTest {
    private val clock = FakeClock()
    private fun engine() = ExcursionEngine("T", ThresholdProfile(), 60, clock, startUtcMs = 0L)

    private fun ExcursionEngine.feed(vararg rs: Reading): List<AlarmEvent> = rs.flatMap { onReading(it) }

    @Test
    fun opensAfterTwoConsecutiveReadingsWithStartAtFirst() {
        val e = engine()
        assertTrue(e.feed(r(0, 5.0), r(60, 9.0)).isEmpty())
        val ev = e.onReading(r(120, 9.5))
        val opened = assertIs<AlarmEvent.ExcursionOpened>(ev.single())
        assertEquals(ExcursionType.HEAT, opened.excursion.type)
        assertEquals(60_000L, opened.excursion.startUtcMs)
        assertEquals(9.5, opened.excursion.peakC, 1e-9)
    }

    @Test
    fun singleSpikeDoesNotOpen() {
        val e = engine()
        e.feed(r(0, 5.0), r(60, 12.0), r(120, 5.0), r(180, 12.0), r(240, 5.0))
        assertTrue(e.excursions().isEmpty())
    }

    @Test
    fun closesAfterTwoReadingsInsideHysteresisBandEndAtFirstReturn() {
        val e = engine()
        e.feed(r(0, 9.0), r(60, 9.0)) // open, start 0
        assertTrue(e.feed(r(120, 7.7)).isEmpty()) // 7.7 <= 7.8, inside band
        val ev = e.onReading(r(180, 7.7))
        val closed = assertIs<AlarmEvent.ExcursionClosed>(ev.single())
        assertEquals(120_000L, closed.excursion.endUtcMs)
        assertEquals(0L, closed.excursion.startUtcMs)
    }

    @Test
    fun doesNotFlapAroundTheBoundary() {
        val e = engine()
        e.feed(r(0, 9.0), r(60, 9.0))
        // oscillate around max 8.0 without two consecutive readings inside [2.2, 7.8]
        e.feed(r(120, 7.9), r(180, 8.1), r(240, 7.9), r(300, 8.1), r(360, 7.9), r(420, 7.7), r(480, 8.2))
        assertEquals(1, e.excursions().size)
        assertTrue(e.excursions().single().isOpen)
    }

    @Test
    fun readingInRangeButOutsideBandResetsReturnCount() {
        val e = engine()
        e.feed(r(0, 9.0), r(60, 9.0), r(120, 7.5), r(180, 7.9), r(240, 7.5))
        assertTrue(e.excursions().single().isOpen)
    }

    @Test
    fun heatThenColdMakesTwoRecords() {
        val e = engine()
        val ev = e.feed(r(0, 9.0), r(60, 9.0), r(120, 1.5), r(180, 1.0))
        val all = e.excursions()
        assertEquals(listOf(ExcursionType.HEAT, ExcursionType.COLD), all.map { it.type })
        assertEquals(120_000L, all[0].endUtcMs)
        assertEquals(120_000L, all[1].startUtcMs)
        assertTrue(all[1].isOpen)
        assertEquals(1.0, all[1].peakC, 1e-9)
        assertEquals(1, ev.count { it is AlarmEvent.ExcursionClosed })
        assertEquals(2, ev.count { it is AlarmEvent.ExcursionOpened })
    }

    @Test
    fun singleOppositeReadingDoesNotSwitch() {
        val e = engine()
        e.feed(r(0, 9.0), r(60, 9.0), r(120, 1.5), r(180, 9.5))
        assertEquals(listOf(ExcursionType.HEAT), e.excursions().map { it.type })
    }

    @Test
    fun coldEscalatesToFreezeOnSameRecord() {
        val e = engine()
        e.feed(r(0, 1.5), r(60, 1.0))
        val ev = e.feed(r(120, -0.5))
        assertIs<AlarmEvent.FreezeRisk>(ev.single())
        val all = e.excursions()
        assertEquals(1, all.size)
        assertEquals(ExcursionType.FREEZE, all[0].type)
        assertEquals(0L, all[0].startUtcMs)
        assertEquals(-0.5, all[0].peakC, 1e-9)
        assertEquals(2, all[0].severity)
        // warming above freezing but still cold keeps FREEZE
        e.feed(r(180, 1.0))
        assertEquals(ExcursionType.FREEZE, e.excursions().single().type)
    }

    @Test
    fun openingDirectlyAsFreezeEmitsOpenedAndFreezeRisk() {
        val e = engine()
        val ev = e.feed(r(0, 0.0), r(60, -1.0))
        assertEquals(listOf("ExcursionOpened", "FreezeRisk"), ev.map { it::class.simpleName })
        assertEquals(ExcursionType.FREEZE, e.excursions().single().type)
    }

    @Test
    fun runOfColdThenFreezeBeforeOpeningUsesMostSevereType() {
        val e = engine()
        e.feed(r(0, 1.5), r(60, -1.0))
        assertEquals(ExcursionType.FREEZE, e.excursions().single().type)
        assertEquals(0L, e.excursions().single().startUtcMs)
    }

    @Test
    fun sensorLostAndRecovered() {
        val e = engine()
        e.feed(r(0, 5.0), r(60, 5.0))
        assertTrue(e.onTick(60_000L + 300_000L).isEmpty()) // exactly threshold, not yet
        val lost = assertIs<AlarmEvent.SensorLost>(e.onTick(60_000L + 300_001L).single())
        assertEquals(60_000L, lost.lastReadingUtcMs)
        assertTrue(e.onTick(60_000L + 400_000L).isEmpty()) // reported once
        val rec = assertIs<AlarmEvent.SensorRecovered>(e.onReading(r(500, 5.0)).single())
        assertEquals(500_000L, rec.tsUtcMs)
        assertTrue(e.onTick(501_000L).isEmpty())
    }

    @Test
    fun onTickDefaultsToInjectedClock() {
        val e = engine()
        clock.now = 400_000L
        assertIs<AlarmEvent.SensorLost>(e.onTick().single())
    }

    @Test
    fun lateReadingWithoutTickStillEmitsLostAndRecovered() {
        val e = engine()
        e.feed(r(0, 5.0))
        val ev = e.onReading(r(1000, 5.0))
        assertEquals(listOf("SensorLost", "SensorRecovered"), ev.map { it::class.simpleName })
    }

    @Test
    fun gapBreaksConsecutiveCountButKeepsOpenExcursion() {
        val e = engine()
        e.feed(r(0, 9.0), r(60, 9.0)) // open
        e.feed(r(120, 5.0), r(2000, 5.0)) // gap between the in-band readings
        assertTrue(e.excursions().single().isOpen)
        e.feed(r(2060, 5.0))
        assertEquals(2_000_000L, e.excursions().single().endUtcMs)
    }

    @Test
    fun rebuiltStateEqualsLiveState() {
        val readings = listOf(
            r(0, 5.0), r(60, 9.0), r(120, 9.5), r(180, 10.0), r(240, 7.7), r(300, 7.0),
            r(360, 1.5), r(420, 1.0), r(480, -0.5), r(540, 3.0), r(600, 3.0), r(660, 3.0),
            r(1500, 5.0), r(1560, 9.0),
        )
        val live = engine()
        readings.forEach { live.onReading(it) }
        val rebuilt = ExcursionEngine.rebuild("T", ThresholdProfile(), 60, clock, 0L, readings.shuffled(kotlin.random.Random(1)))
        assertEquals(live.snapshot(), rebuilt.snapshot())
        assertNotNull(live.excursions().firstOrNull())
    }

    @Test
    fun rebuildOfEmptyHistoryIsFreshEngine() {
        val fresh = engine().snapshot()
        assertEquals(fresh, ExcursionEngine.rebuild("T", ThresholdProfile(), 60, clock, 0L, emptyList()).snapshot())
        assertNull(fresh.open)
    }
}
