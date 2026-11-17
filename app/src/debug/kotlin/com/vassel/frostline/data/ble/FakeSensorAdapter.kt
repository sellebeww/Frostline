// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.SensorAdapter
import com.vassel.frostline.domain.port.SensorEvent
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One piece of a simulated temperature timeline. Durations are simulated milliseconds. */
sealed interface SimStep {
    val durationMs: Long

    data class Hold(val tempC: Double, override val durationMs: Long) : SimStep
    data class Ramp(val fromC: Double, val toC: Double, override val durationMs: Long) : SimStep
    data class Oscillate(val centerC: Double, val amplitudeC: Double, val periodMs: Long, override val durationMs: Long) : SimStep

    /** The sensor goes silent and disconnects, then comes back. */
    data class Silence(override val durationMs: Long) : SimStep
}

/**
 * Debug-only simulator. Emits one sample every [sampleEveryMs] of simulated time and waits sampleEveryMs / [speed] of
 * real time, so [speed] 10 plays ten simulated seconds per real second. Completes when the timeline ends.
 */
class FakeSensorAdapter(
    private val steps: List<SimStep>,
    private val speed: Double = 1.0,
    private val sampleEveryMs: Long = 10_000,
) : SensorAdapter {
    override fun events(): Flow<SensorEvent> = flow {
        emit(SensorEvent.Connection(ConnectionState.CONNECTED))
        for (step in steps) {
            if (step is SimStep.Silence) {
                emit(SensorEvent.Connection(ConnectionState.DISCONNECTED))
                delay((step.durationMs / speed).toLong())
                emit(SensorEvent.Connection(ConnectionState.CONNECTED))
                continue
            }
            var t = 0L
            while (t < step.durationMs) {
                emit(SensorEvent.Sample(valueAt(step, t), 55.0))
                delay((sampleEveryMs / speed).toLong())
                t += sampleEveryMs
            }
        }
    }

    private fun valueAt(step: SimStep, t: Long): Double = when (step) {
        is SimStep.Hold -> step.tempC
        is SimStep.Ramp -> step.fromC + (step.toC - step.fromC) * t / step.durationMs
        is SimStep.Oscillate -> step.centerC + step.amplitudeC * sin(2 * PI * t / step.periodMs)
        is SimStep.Silence -> error("no value during silence")
    }
}

/** Real time scaled by [speed] around [anchorUtcMs], so simulated time stays continuous across service restarts. */
class ScaledClock(private val base: Clock, private val speed: Double, private val anchorUtcMs: Long) : Clock {
    override fun nowUtcMs(): Long = anchorUtcMs + ((base.nowUtcMs() - anchorUtcMs) * speed).toLong()
    override fun elapsedRealtimeMs(): Long = (base.elapsedRealtimeMs() * speed).toLong()
}

object SimScenarios {
    private const val MIN = 60_000L

    val stable = listOf(SimStep.Hold(5.0, 30 * MIN))
    val heat = listOf(SimStep.Hold(5.0, 5 * MIN), SimStep.Ramp(5.0, 10.0, 30 * MIN), SimStep.Hold(10.0, 10 * MIN))
    val freeze = listOf(SimStep.Hold(5.0, 5 * MIN), SimStep.Ramp(5.0, -1.0, 30 * MIN), SimStep.Hold(-1.0, 10 * MIN))
    val dropout = listOf(SimStep.Hold(5.0, 5 * MIN), SimStep.Silence(10 * MIN), SimStep.Hold(5.0, 5 * MIN))
    // Centred just above the 8.0 limit with +-0.25: it opens one excursion and, thanks to the 0.2 hysteresis, stays open.
    // (Centre 8.0 would alias with the 60 s log interval and never show a pair of readings above the limit.)
    val oscillate = listOf(SimStep.Oscillate(8.1, 0.25, 3 * MIN, 20 * MIN))

    /** The full demo: stable, warming to 10, cooling to -1, back to 5, sensor dropout for 10 minutes, oscillation at 8. */
    val all = listOf(
        SimStep.Hold(5.0, 10 * MIN),
        SimStep.Ramp(5.0, 10.0, 30 * MIN),
        SimStep.Hold(10.0, 10 * MIN),
        SimStep.Ramp(10.0, -1.0, 40 * MIN),
        SimStep.Hold(-1.0, 10 * MIN),
        SimStep.Ramp(-1.0, 5.0, 20 * MIN),
        SimStep.Silence(10 * MIN),
        SimStep.Hold(5.0, 5 * MIN),
        SimStep.Oscillate(8.1, 0.25, 3 * MIN, 20 * MIN),
    )
}
