// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.FakeClock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TapGateTest {
    private val clock = FakeClock(0)
    private val gate = TapGate(clock)

    @Test
    fun firstTapIsAccepted() = assertTrue(gate.accept())

    @Test
    fun aSecondTapWithinThreeSecondsIsIgnoredAndDoesNotExtendTheWindow() {
        assertTrue(gate.accept())
        clock.now = 2_999
        assertFalse(gate.accept())
        clock.now = 3_000 // measured from the accepted tap, not from the ignored one
        assertTrue(gate.accept())
    }

    @Test
    fun tapsFarApartAreAllAccepted() {
        repeat(3) { i ->
            clock.now = i * 10_000L
            assertTrue(gate.accept())
        }
    }
}
