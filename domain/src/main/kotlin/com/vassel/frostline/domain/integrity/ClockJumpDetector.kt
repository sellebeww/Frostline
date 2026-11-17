// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.Reading
import kotlin.math.abs

data class ClockJump(val atUtcMs: Long, val driftMs: Long)

/** Compares wall-clock deltas with monotonic deltas to find clock changes (reported as a warning). */
object ClockJumpDetector {
    /** [readings] must be in capture order. A pair whose monotonic clock went backwards (reboot) is skipped because the two clocks are not comparable. */
    fun detect(readings: List<Reading>, toleranceMs: Long = 5_000L): List<ClockJump> {
        val jumps = mutableListOf<ClockJump>()
        for (i in 1 until readings.size) {
            val wall = readings[i].tsUtcMs - readings[i - 1].tsUtcMs
            val mono = readings[i].elapsedMs - readings[i - 1].elapsedMs
            if (mono < 0) continue
            val drift = wall - mono
            if (abs(drift) > toleranceMs) jumps += ClockJump(readings[i].tsUtcMs, drift)
        }
        return jumps
    }
}
