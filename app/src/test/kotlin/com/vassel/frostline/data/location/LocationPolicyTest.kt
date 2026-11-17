// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.location

import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationResult
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationPolicyTest {
    private val now = 1_000_000L
    private fun fix(ageMs: Long) = GeoLocation(1.0, 2.0, 10.0, now - ageMs)

    @Test
    fun aFreshFixWinsAndIsNotApproximate() {
        assertEquals(LocationResult.Fix(fix(0)), LocationPolicy.choose(fix(0), fix(5_000), now, timedOut = false))
    }

    @Test
    fun aCachedFixYoungerThanTwoMinutesIsAcceptedAsApproximate() {
        assertEquals(LocationResult.Fix(fix(119_999), approximate = true), LocationPolicy.choose(null, fix(119_999), now, timedOut = true))
    }

    @Test
    fun aCachedFixOfTwoMinutesOrOlderIsRejected() {
        assertEquals(LocationResult.None(LocationFailure.TIMEOUT), LocationPolicy.choose(null, fix(120_000), now, timedOut = true))
        assertEquals(LocationResult.None(LocationFailure.UNAVAILABLE), LocationPolicy.choose(null, fix(500_000), now, timedOut = false))
    }

    @Test
    fun aFixFromTheFutureIsRejected() {
        assertEquals(LocationResult.None(LocationFailure.UNAVAILABLE), LocationPolicy.choose(null, fix(-1_000), now, timedOut = false))
    }

    @Test
    fun noFixAtAllGivesAReason() {
        assertEquals(LocationResult.None(LocationFailure.TIMEOUT), LocationPolicy.choose(null, null, now, timedOut = true))
        assertEquals(LocationResult.None(LocationFailure.UNAVAILABLE), LocationPolicy.choose(null, null, now, timedOut = false))
    }
}
