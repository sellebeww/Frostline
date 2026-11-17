// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.FakeClock
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.JcaSigner
import com.vassel.frostline.domain.integrity.newKeyPair
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.r
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCasesTest {
    private val clock = FakeClock(0L)
    private val trips = FakeTrips()
    private val readings = FakeReadings()
    private val tx = FakeTransactions()
    private val custody = FakeCustody(tx)
    private val excursions = FakeExcursions()
    private val location = FakeLocation()
    private val pair = newKeyPair()
    private val signer = JcaSigner(pair)
    private var idCounter = 0

    private val startTrip = StartTrip(trips, clock) { "trip-${++idCounter}" }
    private fun logReading() = LogReading(trips, readings, excursions, clock)
    private val record = RecordCustodyEvent(trips, readings, custody, excursions, location, signer, tx, clock)
    private val complete = CompleteTrip(record, trips, excursions, tx)

    private fun buildReport() = BuildReportData(
        trips, readings, excursions, custody, clock = clock,
        carriers = object : com.vassel.frostline.domain.port.CarrierRepository {
            override suspend fun getAll() = emptyList<com.vassel.frostline.domain.model.Carrier>()
            override suspend fun create(name: String, capacityLiters: Double?) = error("unused")
        },
        sensors = object : com.vassel.frostline.domain.port.SensorRepository {
            override suspend fun pair(address: String, model: String?, nowUtcMs: Long) = error("unused")
            override suspend fun getPaired() = null
            override suspend fun get(id: String) = null
        },
    )

    @Test
    fun startTripCreatesActiveTripAndRejectsSecondActive() = runTest {
        clock.now = 5_000
        val t = startTrip(carrierId = "box-1", operatorId = "op", logIntervalSec = 60)
        assertEquals(TripStatus.ACTIVE, t.status)
        assertEquals(5_000L, t.startedUtcMs)
        assertEquals(t, trips.getActive())
        assertFailsWith<IllegalStateException> { startTrip(null, "op") }
    }

    @Test
    fun logReadingPersistsAndOpensExcursion() = runTest {
        val t = startTrip(null, "op")
        val log = logReading()
        log(t.id, r(0, 5.0))
        log(t.id, r(60, 9.0))
        val ev = log(t.id, r(120, 9.5))
        assertEquals(1, ev.size)
        assertEquals(3, readings.getAll(t.id).size)
        val ex = excursions.getAll(t.id).single()
        assertEquals(ExcursionType.HEAT, ex.type)
        assertTrue(ex.isOpen)
        log(t.id, r(180, 11.0)) // peak updated without an alarm
        assertEquals(11.0, excursions.getAll(t.id).single().peakC, 1e-9)
    }

    @Test
    fun newLogReadingInstanceResumesFromStoredHistory() = runTest {
        val t = startTrip(null, "op")
        val first = logReading()
        first(t.id, r(0, 9.0))
        first(t.id, r(60, 9.0)) // process dies here, excursion open
        val resumed = logReading()
        resumed(t.id, r(120, 5.0))
        val ev = resumed(t.id, r(180, 5.0))
        assertEquals(1, ev.size)
        assertEquals(120_000L, excursions.getAll(t.id).single().endUtcMs)
    }

    @Test
    fun startTripKeepsTheSensorId() = runTest {
        assertEquals("sensor-1", startTrip(null, "op", sensorId = "sensor-1").sensorId)
    }

    @Test
    fun onTickReportsSensorLostOnceAfterSilenceAndSurvivesRestart() = runTest {
        val t = startTrip(null, "op")
        val log = logReading()
        log(t.id, r(0, 5.0))
        clock.now = 300_000
        assertEquals(emptyList(), log.onTick(t.id))
        clock.now = 301_000
        assertEquals(1, log.onTick(t.id).size)
        assertEquals(emptyList(), log.onTick(t.id)) // reported once
        // a new instance (process restart) rebuilds from stored readings and reports the silence again
        assertEquals(1, logReading().onTick(t.id).size)
        assertFailsWith<IllegalStateException> { log.onTick("nope") }
    }

    @Test
    fun logReadingRejectsUnknownAndCompletedTrips() = runTest {
        assertFailsWith<IllegalStateException> { logReading()("nope", r(0, 5.0)) }
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "tag1", "A", "op")
        complete(t.id, "tag2", "B", "op")
        assertFailsWith<IllegalStateException> { logReading()(t.id, r(10_000, 5.0)) }
    }

    @Test
    fun custodyEventsFormValidChainWithDigestTempAndLocation() = runTest {
        val t = startTrip(null, "op")
        val log = logReading()
        log(t.id, r(0, 5.0)); log(t.id, r(60, 5.5))
        clock.now = 60_000
        val pickup = record(t.id, CustodyType.PICKUP, "tag1", "A", "op")
        assertEquals(550, pickup.tempCenti)
        assertEquals(-6.2, pickup.lat)
        assertEquals(false, pickup.excursionOpen)
        log(t.id, r(120, 9.0)); log(t.id, r(180, 9.0))
        clock.now = 200_000
        val handoff = record(t.id, CustodyType.HANDOFF, "tag2", "B", "op")
        assertEquals(true, handoff.excursionOpen)
        assertEquals(pickup.hash, handoff.prevHash)
        assertTrue(handoff.readingsDigest != pickup.readingsDigest)
        clock.now = 300_000
        val delivered = complete(t.id, "tag3", "C", "op")
        assertEquals(CustodyType.DELIVERY, delivered.type)
        assertEquals(TripStatus.COMPLETED, trips.get(t.id)!!.status)
        assertEquals(300_000L, trips.get(t.id)!!.endedUtcMs)
        assertEquals(ChainVerification.Valid, com.vassel.frostline.domain.integrity.ChainVerifier.verify(custody.getAll(t.id), pair.public))
    }

    @Test
    fun custodyAppendHappensInsideATransaction() = runTest {
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "t", "x", "op")
        assertEquals(true, custody.appendedInsideTransaction)
    }

    @Test
    fun completeTripClosesOpenExcursionAtDeliveryInOneTransaction() = runTest {
        val t = startTrip(null, "op")
        val log = logReading()
        log(t.id, r(0, 9.0)); log(t.id, r(60, 9.0))
        record(t.id, CustodyType.PICKUP, "t", "x", "op")
        clock.now = 500_000
        val runsBefore = tx.runs
        val delivered = complete(t.id, "t2", "y", "op")
        assertEquals(500_000L, excursions.getAll(t.id).single().endUtcMs)
        assertEquals(500_000L, delivered.tsUtcMs)
        assertEquals(500_000L, trips.get(t.id)!!.endedUtcMs)
        assertEquals(2, tx.maxDepth) // CompleteTrip transaction wraps the nested record transaction
        assertEquals(runsBefore + 2, tx.runs)
    }

    @Test
    fun missingLocationIsRecordedAsAbsentAndFirstEventMustBePickup() = runTest {
        location.result = LocationResult.None(LocationFailure.PERMISSION_DENIED)
        val t = startTrip(null, "op")
        assertFailsWith<IllegalArgumentException> { record(t.id, CustodyType.HANDOFF, "t", "x", "op") }
        val e = record(t.id, CustodyType.PICKUP, "t", "x", "op")
        assertNull(e.lat); assertNull(e.lon); assertNull(e.accuracyM); assertNull(e.tempCenti)
    }

    @Test
    fun locationFailureStillStoresTheEventWithAnExplainingDiagnostic() = runTest {
        location.result = LocationResult.None(LocationFailure.TIMEOUT)
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "t", "x", "op", tagUidHash = "ab12cd34ef56")
        assertEquals("uid=ab12cd34ef56;loc=none:TIMEOUT", custody.diagnostics.single())
        assertNull(custody.list.single().lat)
    }

    @Test
    fun anApproximateFixIsStoredAndFlagged() = runTest {
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "t", "x", "op", location = LocationResult.Fix(GeoLocation(-1.0, 2.0, 30.0, 0L), approximate = true))
        assertEquals("loc=approximate", custody.diagnostics.single())
        assertEquals(-1.0, custody.list.single().lat)
    }

    @Test
    fun theLocationTakenAtTheTapIsUsedInsteadOfFetchingAgain() = runTest {
        location.result = LocationResult.None(LocationFailure.UNAVAILABLE)
        val t = startTrip(null, "op")
        val e = record(t.id, CustodyType.PICKUP, "t", "x", "op", location = LocationResult.Fix(GeoLocation(5.0, 6.0, 4.0, 0L)))
        assertEquals(5.0, e.lat)
        assertEquals("loc=ok", custody.diagnostics.single())
    }

    @Test
    fun logReadingAnalysesTheReadingAsTheRepositoryStoredIt() = runTest {
        val trip = startTrip(null, "op")
        readings.restamp = { it.copy(tsUtcMs = it.tsUtcMs + 5_000) } // what the repository does for a reading below a custody event
        val log = logReading()
        log(trip.id, Reading(1_000, 1_000, 12.0))
        log(trip.id, Reading(2_000, 2_000, 12.0))
        assertEquals(6_000L, excursions.getAll(trip.id).single().startUtcMs) // the stored timestamp, not the captured one
    }

    @Test
    fun recordRejectsDeliveryAndEventsAfterCompletion() = runTest {
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "t", "x", "op")
        assertFailsWith<IllegalArgumentException> { record(t.id, CustodyType.DELIVERY, "t", "x", "op") }
        complete(t.id, "t", "x", "op")
        assertFailsWith<IllegalStateException> { record(t.id, CustodyType.HANDOFF, "t", "x", "op") }
        assertFailsWith<IllegalStateException> { complete(t.id, "t", "x", "op") }
    }

    @Test
    fun buildReportDataCombinesEverything() = runTest {
        val t = startTrip(null, "op")
        val log = logReading()
        listOf(r(0, 5.0), r(60, 9.0), r(120, 9.0), r(180, 5.0), r(240, 5.0)).forEach { log(t.id, it) }
        clock.now = 240_000
        record(t.id, CustodyType.PICKUP, "t", "x", "op")
        complete(t.id, "t2", "y", "op")
        val data = buildReport()(t.id, pair.public)
        assertEquals(t.id, data.trip.id)
        assertEquals(5, data.readings.size)
        assertEquals(1, data.stats.excursionCount)
        assertEquals(2, data.custody.size)
        assertEquals(ChainVerification.Valid, data.chainVerification)
        assertTrue(data.clockJumps.isEmpty())
        assertNotNull(data.stats.mktC)
        assertTrue(data.stats.outOfRangeMs > 0)
    }

    @Test
    fun buildReportDataFlagsTamperedChain() = runTest {
        val t = startTrip(null, "op")
        record(t.id, CustodyType.PICKUP, "t", "x", "op")
        record(t.id, CustodyType.HANDOFF, "t", "x", "op")
        custody.list[1] = custody.list[1].copy(userId = "mallory")
        val data = buildReport()(t.id, pair.public)
        assertTrue(data.chainVerification is ChainVerification.Invalid)
    }
}
