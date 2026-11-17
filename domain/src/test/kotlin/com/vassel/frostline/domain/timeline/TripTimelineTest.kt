// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.timeline

import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.integrity.JcaSigner
import com.vassel.frostline.domain.integrity.fields
import com.vassel.frostline.domain.integrity.newKeyPair
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.GapInfo
import com.vassel.frostline.domain.r
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TripTimelineTest {
    private val signer = JcaSigner(newKeyPair())
    private fun custodyAt(sec: Long, type: CustodyType = CustodyType.PICKUP) =
        HashChain.appendEvent(null, fields(type, sec * 1000), signer)

    @Test
    fun emptyTripHasNoItems() {
        assertEquals(emptyList(), TripTimeline.build(emptyList(), emptyList(), emptyList(), emptyList(), 60))
    }

    @Test
    fun onlyReadingsBecomeASingleSummary() {
        val items = TripTimeline.build(listOf(r(0, 4.0), r(60, 6.0), r(120, 5.0)), emptyList(), emptyList(), emptyList(), 60)
        val s = assertIs<TimelineItem.ReadingsSummary>(items.single())
        assertEquals(3, s.count); assertEquals(4.0, s.minC); assertEquals(6.0, s.maxC)
    }

    @Test
    fun mergesReadingsExcursionsAndCustodyInTimeOrder() {
        val readings = (0..10).map { r(it * 60L, if (it in 3..5) 9.0 else 5.0) }
        val ex = Excursion("t", ExcursionType.HEAT, 180_000, 360_000, 9.0)
        val items = TripTimeline.build(readings, listOf(ex), listOf(custodyAt(30), custodyAt(570)), emptyList(), 60)
        assertEquals(
            listOf("ReadingsSummary", "Custody", "ReadingsSummary", "ExcursionStarted", "ReadingsSummary", "ExcursionEnded", "ReadingsSummary", "Custody", "ReadingsSummary"),
            items.map { it::class.simpleName },
        )
        assertEquals(items.map { it.tsUtcMs }, items.map { it.tsUtcMs }.sorted())
        // the summary inside the excursion holds the hot readings
        val inside = items.filterIsInstance<TimelineItem.ReadingsSummary>()[2]
        assertEquals(9.0, inside.minC); assertEquals(3, inside.count)
    }

    @Test
    fun anOpenExcursionHasNoEndItem() {
        val ex = Excursion("t", ExcursionType.FREEZE, 60_000, null, -1.0)
        val items = TripTimeline.build(listOf(r(0, 5.0), r(60, -1.0)), listOf(ex), emptyList(), emptyList(), 60)
        assertTrue(items.none { it is TimelineItem.ExcursionEnded })
        assertEquals(ExcursionType.FREEZE, items.filterIsInstance<TimelineItem.ExcursionStarted>().single().excursion.type)
    }

    @Test
    fun gapsBecomeLostAndRecoveredAtTheAlarmAndReturnTimes() {
        val gap = GapInfo(60_000, 1_000_000)
        val items = TripTimeline.build(listOf(r(0, 5.0), r(60, 5.0), r(1000, 5.0)), emptyList(), emptyList(), listOf(gap), 60)
        val lost = items.filterIsInstance<TimelineItem.SensorLost>().single()
        assertEquals(60_000 + 300_000L, lost.tsUtcMs) // last reading plus the 300 s threshold
        assertEquals(1_000_000L, items.filterIsInstance<TimelineItem.SensorRecovered>().single().tsUtcMs)
    }

    @Test
    fun alarmHistoryKeepsOnlyAlarmItems() {
        val ex = Excursion("t", ExcursionType.HEAT, 180_000, 360_000, 9.0)
        val items = TripTimeline.build((0..10).map { r(it * 60L, 5.0) }, listOf(ex), listOf(custodyAt(30)), listOf(GapInfo(0, 600_000)), 60)
        val alarms = TripTimeline.alarms(items)
        assertEquals(setOf("ExcursionStarted", "ExcursionEnded", "SensorLost", "SensorRecovered"), alarms.map { it::class.simpleName }.toSet())
    }
}
