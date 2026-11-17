// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.timeline

import com.vassel.frostline.domain.engine.Weighting
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.GapInfo
import com.vassel.frostline.domain.model.Reading

sealed interface TimelineItem {
    val tsUtcMs: Long

    /** Readings between two significant items, condensed to one row. */
    data class ReadingsSummary(override val tsUtcMs: Long, val toUtcMs: Long, val count: Int, val minC: Double, val maxC: Double) : TimelineItem

    /** The excursion type tells HEAT, COLD or FREEZE (freeze risk). */
    data class ExcursionStarted(override val tsUtcMs: Long, val excursion: Excursion) : TimelineItem
    data class ExcursionEnded(override val tsUtcMs: Long, val excursion: Excursion) : TimelineItem
    data class Custody(override val tsUtcMs: Long, val event: CustodyEvent) : TimelineItem

    /** [tsUtcMs] is when the watchdog would have raised the alarm (last reading plus the gap threshold). */
    data class SensorLost(override val tsUtcMs: Long, val gap: GapInfo) : TimelineItem
    data class SensorRecovered(override val tsUtcMs: Long, val gap: GapInfo) : TimelineItem
}

/** One merged, time ordered view of a trip: readings (summarised), excursions, custody events and data gaps. */
object TripTimeline {
    /** A span without readings longer than this is a gap (chart breaks, SensorLost). */
    fun gapThresholdMs(intervalSec: Int): Long = Weighting.gapThresholdMs(intervalSec)

    fun build(
        readings: List<Reading>,
        excursions: List<Excursion>,
        custody: List<CustodyEvent>,
        gaps: List<GapInfo>,
        intervalSec: Int,
    ): List<TimelineItem> {
        val threshold = Weighting.gapThresholdMs(intervalSec)
        val significant = buildList<TimelineItem> {
            for (e in excursions) {
                add(TimelineItem.ExcursionStarted(e.startUtcMs, e))
                e.endUtcMs?.let { add(TimelineItem.ExcursionEnded(it, e)) }
            }
            custody.forEach { add(TimelineItem.Custody(it.tsUtcMs, it)) }
            for (g in gaps) {
                add(TimelineItem.SensorLost(g.startUtcMs + threshold, g))
                add(TimelineItem.SensorRecovered(g.endUtcMs, g))
            }
        }.sortedBy { it.tsUtcMs }

        val sorted = readings.sortedBy { it.tsUtcMs }
        val segments = sorted.groupBy { r -> significant.count { it.tsUtcMs <= r.tsUtcMs } }
        val summaries = segments.values.map { seg ->
            TimelineItem.ReadingsSummary(seg.first().tsUtcMs, seg.last().tsUtcMs, seg.size, seg.minOf { it.tempC }, seg.maxOf { it.tempC })
        }
        // At equal timestamps the significant item comes first.
        return (significant.map { 0 to it } + summaries.map { 1 to it }).sortedWith(compareBy({ it.second.tsUtcMs }, { it.first })).map { it.second }
    }

    /** The alarm history: what raised or cleared an alarm. */
    fun alarms(items: List<TimelineItem>): List<TimelineItem> = items.filter {
        it is TimelineItem.ExcursionStarted || it is TimelineItem.ExcursionEnded || it is TimelineItem.SensorLost || it is TimelineItem.SensorRecovered
    }
}
