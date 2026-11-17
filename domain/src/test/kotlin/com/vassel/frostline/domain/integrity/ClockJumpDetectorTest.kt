// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.Reading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClockJumpDetectorTest {
    private fun rd(ts: Long, el: Long) = Reading(ts, el, 5.0)

    @Test
    fun steadyClockHasNoJumps() {
        assertTrue(ClockJumpDetector.detect(listOf(rd(0, 0), rd(60_000, 60_100), rd(120_000, 120_000))).isEmpty())
    }

    @Test
    fun wallClockJumpIsReported() {
        val jumps = ClockJumpDetector.detect(listOf(rd(0, 0), rd(60_000, 60_000), rd(7_200_000, 120_000)))
        assertEquals(1, jumps.size)
        assertEquals(7_200_000L, jumps[0].atUtcMs)
        assertEquals(7_200_000L - 60_000L - 60_000L, jumps[0].driftMs)
    }

    @Test
    fun backwardJumpIsReported() {
        assertEquals(1, ClockJumpDetector.detect(listOf(rd(100_000, 0), rd(40_000, 60_000))).size)
    }

    @Test
    fun rebootResetOfMonotonicClockIsNotReportedAsJump() {
        assertTrue(ClockJumpDetector.detect(listOf(rd(0, 900_000), rd(120_000, 5_000))).isEmpty())
    }
}
