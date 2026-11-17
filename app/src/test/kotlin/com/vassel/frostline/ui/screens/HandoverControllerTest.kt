// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.db.JcaSigner
import com.vassel.frostline.data.db.RoomCustodyRepository
import com.vassel.frostline.data.db.RoomExcursionRepository
import com.vassel.frostline.data.db.RoomReadingRepository
import com.vassel.frostline.data.db.RoomTransactionRunner
import com.vassel.frostline.data.db.RoomTripRepository
import com.vassel.frostline.data.db.TestClock
import com.vassel.frostline.data.db.inMemoryDb
import com.vassel.frostline.data.nfc.TagInfo
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.ChainVerifier
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.LogReading
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.StartTrip
import com.vassel.frostline.domain.usecase.TapGate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The whole tap flow on real Room repositories with a fake location, a software signer and a controllable clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandoverControllerTest {
    private lateinit var db: FrostlineDatabase
    private val clock = TestClock(1_000_000L)
    private val signer = JcaSigner()
    private var result: LocationResult = LocationResult.Fix(GeoLocation(-6.2, 106.8, 5.0, 0L))
    private val location = object : LocationProvider { override suspend fun currentLocation() = result }
    private var stopped = 0

    private lateinit var trips: RoomTripRepository
    private lateinit var custody: RoomCustodyRepository
    private lateinit var excursions: RoomExcursionRepository
    private lateinit var readings: RoomReadingRepository
    private lateinit var controller: HandoverController

    private val tagA = TagInfo("123e4567-e89b-12d3-a456-426614174000", "Gudang")
    private val tagB = TagInfo("223e4567-e89b-12d3-a456-426614174000", "Puskesmas")
    private val tagC = TagInfo("323e4567-e89b-12d3-a456-426614174000", "Posyandu")

    @Before
    fun setUp() {
        db = inMemoryDb()
        trips = RoomTripRepository(db); custody = RoomCustodyRepository(db); excursions = RoomExcursionRepository(db); readings = RoomReadingRepository(db)
        val tx = RoomTransactionRunner(db)
        val record = RecordCustodyEvent(trips, readings, custody, excursions, location, signer, tx, clock)
        controller = HandoverController(trips, readings, custody, record, CompleteTrip(record, trips, excursions, tx), location, TapGate(clock)) { stopped++ }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tap(tag: TagInfo): HandoverCandidate {
        clock.now += 10_000 // taps far apart pass the 3 s debounce
        val outcome = controller.onTag(tag, "uid" + tag.label.take(3))
        return controller.locate(assertIs<TapOutcome.Candidate>(outcome).candidate)
    }

    @Test
    fun pickupHandoffDeliveryProduceAChainThatVerifies() = runTest {
        val trip = StartTrip(trips, clock)(null, "op")
        LogReading(trips, readings, excursions, clock)(trip.id, Reading(clock.now, 1, 5.5))

        val a = tap(tagA)
        assertEquals(CustodyType.PICKUP, a.proposed)
        assertEquals(false, a.canDeliver)
        assertEquals(5.5, a.tempC)
        assertIs<ConfirmOutcome.Recorded>(controller.confirm(a, deliver = false, userId = "user-1"))

        val b = tap(tagB)
        assertEquals(CustodyType.HANDOFF, b.proposed)
        assertEquals(true, b.canDeliver)
        assertIs<ConfirmOutcome.Recorded>(controller.confirm(b, false, "user-1"))

        val c = tap(tagC)
        val done = assertIs<ConfirmOutcome.Recorded>(controller.confirm(c, deliver = true, userId = "user-1"))
        assertTrue(done.tripCompleted)
        assertEquals(1, stopped)
        assertEquals(TripStatus.COMPLETED, trips.get(trip.id)!!.status)

        val events = custody.getAll(trip.id)
        assertEquals(listOf(CustodyType.PICKUP, CustodyType.HANDOFF, CustodyType.DELIVERY), events.map { it.type })
        assertEquals(listOf(tagA.tagId, tagB.tagId, tagC.tagId), events.map { it.tagUid })
        assertEquals(listOf("Gudang", "Puskesmas", "Posyandu"), events.map { it.tagLabel })
        assertEquals(setOf("user-1"), events.map { it.userId }.toSet())
        assertEquals(550, events.first().tempCenti)
        assertEquals(-6.2, events.first().lat)
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(events, signer.publicKey))
    }

    @Test
    fun nothingCanBeRecordedAfterDelivery() = runTest {
        StartTrip(trips, clock)(null, "op")
        controller.confirm(tap(tagA), false, "u")
        controller.confirm(tap(tagB), true, "u")
        clock.now += 10_000
        assertEquals(TapOutcome.NoActiveTrip, controller.onTag(tagC, "x"))
    }

    @Test
    fun theFirstEventCannotBeDeliveryAndNothingIsStored() = runTest {
        val trip = StartTrip(trips, clock)(null, "op")
        val a = tap(tagA)
        assertEquals(ConfirmOutcome.Rejected(RejectReason.INVALID_SEQUENCE), controller.confirm(a, deliver = true, userId = "u"))
        assertEquals(0, custody.getAll(trip.id).size)
        assertEquals(0, stopped)
        assertEquals(TripStatus.ACTIVE, trips.get(trip.id)!!.status)
    }

    @Test
    fun aSecondTapWithinThreeSecondsIsIgnored() = runTest {
        StartTrip(trips, clock)(null, "op")
        clock.now += 10_000
        assertIs<TapOutcome.Candidate>(controller.onTag(tagA, "u"))
        clock.now += 2_999
        assertEquals(TapOutcome.Ignored, controller.onTag(tagA, "u"))
        clock.now += 1
        assertIs<TapOutcome.Candidate>(controller.onTag(tagA, "u"))
    }

    @Test
    fun theSameTagTwiceInARowIsFlaggedForConfirmation() = runTest {
        StartTrip(trips, clock)(null, "op")
        assertEquals(false, tap(tagA).sameTagAsLast)
        controller.confirm(tap(tagA), false, "u")
        assertEquals(true, tap(tagA).sameTagAsLast)
        assertEquals(false, tap(tagB).sameTagAsLast)
    }

    @Test
    fun aFailedLocationStillRecordsTheEventWithoutCoordinates() = runTest {
        result = LocationResult.None(LocationFailure.PERMISSION_DENIED)
        val trip = StartTrip(trips, clock)(null, "op")
        val a = tap(tagA)
        assertEquals(LocationResult.None(LocationFailure.PERMISSION_DENIED), a.location)
        assertIs<ConfirmOutcome.Recorded>(controller.confirm(a, false, "u"))
        val e = custody.getAll(trip.id).single()
        assertNull(e.lat); assertNull(e.lon); assertNull(e.accuracyM)
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(listOf(e), signer.publicKey))
    }

    @Test
    fun theDiagnosticNoteIsStoredNextToTheEventButNotInTheChain() = runTest {
        val trip = StartTrip(trips, clock)(null, "op")
        controller.confirm(tap(tagA), false, "u")
        val diagnostic = db.openHelper.writableDatabase.query("SELECT diagnostic FROM custody_events WHERE trip_id = '${trip.id}'").use { it.moveToFirst(); it.getString(0) }
        assertEquals("uid=uidGud;loc=ok", diagnostic)
    }

    @Test
    fun withoutAnActiveTripATapIsReported() = runTest {
        clock.now += 10_000
        assertEquals(TapOutcome.NoActiveTrip, controller.onTag(tagA, "u"))
    }

    @Test
    fun deliveryClosesTheOpenExcursionAtTheEventTime() = runTest {
        val trip = StartTrip(trips, clock)(null, "op")
        val log = LogReading(trips, readings, excursions, clock)
        log(trip.id, Reading(clock.now, 1, 9.0)); clock.now += 60_000; log(trip.id, Reading(clock.now, 2, 9.0))
        controller.confirm(tap(tagA), false, "u")
        val delivered = controller.confirm(tap(tagB), true, "u")
        val ev = assertIs<ConfirmOutcome.Recorded>(delivered).event
        assertEquals(ev.tsUtcMs, excursions.getAll(trip.id).single().endUtcMs)
        assertEquals(true, custody.getAll(trip.id).first().excursionOpen)
    }
}
