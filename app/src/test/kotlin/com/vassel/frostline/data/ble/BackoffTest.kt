// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackoffTest {
    @Test
    fun doublesFromOneSecondAndCapsAtSixtySecondsWithoutJitter() {
        val b = Backoff(jitter = 0.0)
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000), (0..7).map { b.delayMs(it) })
    }

    @Test
    fun jitterStaysWithinTwentyPercentAndNeverExceedsTheCap() {
        val b = Backoff(random = Random(7))
        for (attempt in 0..40) {
            val exp = minOf(60_000.0, 1_000.0 * Math.pow(2.0, attempt.toDouble()))
            repeat(100) {
                val d = b.delayMs(attempt)
                assertTrue(d <= 60_000, "attempt $attempt gave $d")
                assertTrue(d >= (exp * 0.8).toLong() - 1, "attempt $attempt gave $d")
                assertTrue(d <= (exp * 1.2).toLong() + 1, "attempt $attempt gave $d")
            }
        }
    }

    @Test
    fun delaysActuallyVary() {
        val b = Backoff(random = Random(1))
        assertTrue((1..50).map { b.delayMs(3) }.toSet().size > 10)
    }
}
