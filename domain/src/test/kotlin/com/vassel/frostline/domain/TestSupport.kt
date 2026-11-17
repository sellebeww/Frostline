// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain

import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.port.Clock

/** Reading at [sec] seconds after epoch 0 (elapsed equals wall time). */
fun r(sec: Long, temp: Double) = Reading(tsUtcMs = sec * 1000, elapsedMs = sec * 1000, tempC = temp)

class FakeClock(var now: Long = 0L) : Clock {
    override fun nowUtcMs() = now
    override fun elapsedRealtimeMs() = now
}
