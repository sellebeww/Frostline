// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.engine

import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.port.Clock

/**
 * Stateful excursion detector (SPEC.md 5.2). All comparisons use 0.01 degC integers, so there is no float drift at
 * the thresholds. State can be rebuilt by replaying stored readings with [rebuild].
 */
class ExcursionEngine(
    private val tripId: String,
    thresholds: ThresholdProfile,
    intervalSec: Int,
    private val clock: Clock,
    startUtcMs: Long,
) {
    /** Consecutive readings on one side that have not yet opened (or switched) an excursion. */
    data class Run(val heatSide: Boolean, val firstTsUtcMs: Long, val count: Int, val peakCenti: Int, val worst: ExcursionType)

    /** Consecutive readings back inside the hysteresis band. */
    data class Return(val firstTsUtcMs: Long, val count: Int)

    data class Snapshot(
        val open: Excursion?,
        val closed: List<Excursion>,
        val pending: Run?,
        val opposite: Run?,
        val returning: Return?,
        val lastSignalUtcMs: Long,
        val sensorLost: Boolean,
    )

    private val gapMs = Weighting.gapThresholdMs(intervalSec)
    private val minCenti = centi(thresholds.minC)
    private val maxCenti = centi(thresholds.maxC)
    private val freezeCenti = centi(thresholds.freezeC)

    private var open: Excursion? = null
    private val closed = mutableListOf<Excursion>()
    private var pending: Run? = null
    private var opposite: Run? = null
    private var returning: Return? = null
    private var lastSignal = startUtcMs
    private var lost = false

    fun snapshot() = Snapshot(open, closed.toList(), pending, opposite, returning, lastSignal, lost)

    /** Closed excursions followed by the open one, if any. */
    fun excursions(): List<Excursion> = closed + listOfNotNull(open)

    fun onReading(reading: Reading): List<AlarmEvent> {
        val events = mutableListOf<AlarmEvent>()
        if (lost || reading.tsUtcMs - lastSignal > gapMs) {
            if (!lost) events += AlarmEvent.SensorLost(lastSignal + gapMs, lastSignal)
            events += AlarmEvent.SensorRecovered(reading.tsUtcMs)
            lost = false
            // A gap breaks "consecutive": runs restart, but an open excursion stays open.
            pending = null; opposite = null; returning = null
        }
        lastSignal = reading.tsUtcMs
        val centi = reading.tempCenti
        val type = classify(centi)
        val current = open
        if (current == null) onNoExcursion(reading.tsUtcMs, centi, type, events) else onExcursion(current, reading.tsUtcMs, centi, type, events)
        return events
    }

    /** Detects a silent sensor. Uses the injected clock unless [now] is given. */
    fun onTick(now: Long = clock.nowUtcMs()): List<AlarmEvent> {
        if (lost || now - lastSignal <= gapMs) return emptyList()
        lost = true
        return listOf(AlarmEvent.SensorLost(now, lastSignal))
    }

    private fun onNoExcursion(ts: Long, centi: Int, type: ExcursionType?, events: MutableList<AlarmEvent>) {
        if (type == null) {
            pending = null
            return
        }
        val run = extend(pending, type, ts, centi)
        if (run.count >= 2) {
            pending = null
            openExcursion(run, events)
        } else {
            pending = run
        }
    }

    private fun onExcursion(current: Excursion, ts: Long, centi: Int, type: ExcursionType?, events: MutableList<AlarmEvent>) {
        val heat = current.type == ExcursionType.HEAT
        when {
            type != null && (type == ExcursionType.HEAT) == heat -> {
                opposite = null
                returning = null
                val peak = if (heat) maxOf(current.peakC, centi / 100.0) else minOf(current.peakC, centi / 100.0)
                val escalate = type == ExcursionType.FREEZE && current.type == ExcursionType.COLD
                val updated = current.copy(type = if (escalate) ExcursionType.FREEZE else current.type, peakC = peak)
                open = updated
                if (escalate) events += AlarmEvent.FreezeRisk(updated)
            }
            type != null -> {
                returning = null
                val run = extend(opposite, type, ts, centi)
                if (run.count >= 2) {
                    opposite = null
                    val ended = current.copy(endUtcMs = run.firstTsUtcMs)
                    closed += ended
                    open = null
                    events += AlarmEvent.ExcursionClosed(ended)
                    openExcursion(run, events)
                } else {
                    opposite = run
                }
            }
            centi in (minCenti + HYSTERESIS)..(maxCenti - HYSTERESIS) -> {
                opposite = null
                val back = returning?.let { it.copy(count = it.count + 1) } ?: Return(ts, 1)
                if (back.count >= 2) {
                    returning = null
                    val ended = current.copy(endUtcMs = back.firstTsUtcMs)
                    closed += ended
                    open = null
                    events += AlarmEvent.ExcursionClosed(ended)
                } else {
                    returning = back
                }
            }
            else -> {
                // Inside the range but within the hysteresis margin: neither extends nor closes.
                opposite = null
                returning = null
            }
        }
    }

    private fun extend(previous: Run?, type: ExcursionType, ts: Long, centi: Int): Run {
        val heat = type == ExcursionType.HEAT
        if (previous == null || previous.heatSide != heat) return Run(heat, ts, 1, centi, type)
        return previous.copy(
            count = previous.count + 1,
            peakCenti = if (heat) maxOf(previous.peakCenti, centi) else minOf(previous.peakCenti, centi),
            worst = if (type.severity > previous.worst.severity) type else previous.worst,
        )
    }

    private fun openExcursion(run: Run, events: MutableList<AlarmEvent>) {
        val excursion = Excursion(tripId, run.worst, run.firstTsUtcMs, null, run.peakCenti / 100.0)
        open = excursion
        events += AlarmEvent.ExcursionOpened(excursion)
        if (excursion.type == ExcursionType.FREEZE) events += AlarmEvent.FreezeRisk(excursion)
    }

    private fun classify(centi: Int): ExcursionType? = when {
        centi <= freezeCenti -> ExcursionType.FREEZE
        centi < minCenti -> ExcursionType.COLD
        centi > maxCenti -> ExcursionType.HEAT
        else -> null
    }

    companion object {
        private const val HYSTERESIS = 20 // 0.2 degC

        private fun centi(c: Double) = Math.round(c * 100.0).toInt()

        /** Replays [readings] (any order) into a fresh engine, e.g. after the process was killed. */
        fun rebuild(
            tripId: String,
            thresholds: ThresholdProfile,
            intervalSec: Int,
            clock: Clock,
            startUtcMs: Long,
            readings: List<Reading>,
        ): ExcursionEngine {
            val engine = ExcursionEngine(tripId, thresholds, intervalSec, clock, startUtcMs)
            readings.sortedWith(compareBy({ it.tsUtcMs }, { it.elapsedMs })).forEach { engine.onReading(it) }
            return engine
        }
    }
}
