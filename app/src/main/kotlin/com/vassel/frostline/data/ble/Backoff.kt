// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Reconnect schedule: base x 2^attempt capped at [maxMs], then spread by +-[jitter] and capped again. */
class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 60_000,
    private val jitter: Double = 0.2,
    private val random: Random = Random.Default,
) {
    fun delayMs(attempt: Int): Long {
        val exp = min(maxMs.toDouble(), baseMs * 2.0.pow(min(attempt, 30)))
        val factor = 1.0 + jitter * (random.nextDouble() * 2.0 - 1.0)
        return min(maxMs, (exp * factor).toLong())
    }
}
