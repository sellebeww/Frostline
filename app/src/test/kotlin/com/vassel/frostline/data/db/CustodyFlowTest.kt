// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.ChainVerifier
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.LogReading
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.StartTrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Domain use cases running on real Room repositories (framework SQLite). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustodyFlowTest {
    private lateinit var db: FrostlineDatabase
    private val signer = JcaSigner()
    private val clock = TestClock()

    private lateinit var trips: RoomTripRepository
    private lateinit var readings: RoomReadingRepository
    private lateinit var excursions: RoomExcursionRepository
    private lateinit var custody: RoomCustodyRepository
    private lateinit var tx: RoomTransactionRunner
    private lateinit var record: RecordCustodyEvent

    @Before
    fun setUp() {
        db = inMemoryDb()
        trips = RoomTripRepository(db); readings = RoomReadingRepository(db); excursions = RoomExcursionRepository(db)
        custody = RoomCustodyRepository(db); tx = RoomTransactionRunner(db)
        record = RecordCustodyEvent(trips, readings, custody, excursions, TestLocation(), signer, tx, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startTrip() = StartTrip(trips, clock)(null, "op")

    @Test
    fun fullTripProducesAValidChainAndClosesTheOpenExcursionAtDelivery() = runTest {
        val trip = startTrip()
        val log = LogReading(trips, readings, excursions, clock)
        log(trip.id, Reading(1_000, 1_000, 5.0))
        log(trip.id, Reading(61_000, 61_000, 9.0))
        log(trip.id, Reading(121_000, 121_000, 9.5)) // HEAT excursion opens
        clock.now = 130_000
        val pickup = record(trip.id, CustodyType.PICKUP, "tag1", "A", "op")
        assertEquals(true, pickup.excursionOpen)
        clock.now = 200_000
        record(trip.id, CustodyType.HANDOFF, "tag2", "B", "op")
        clock.now = 300_000
        val delivery = CompleteTrip(record, trips, excursions, tx)(trip.id, "tag3", "C", "op")

        val events = custody.getAll(trip.id)
        assertEquals(listOf(1, 2, 3), events.map { it.seq })
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(events, signer.publicKey))
        assertEquals(300_000L, delivery.tsUtcMs)
        assertEquals(300_000L, excursions.getAll(trip.id).single().endUtcMs)
        assertEquals(TripStatus.COMPLETED, trips.get(trip.id)!!.status)
        assertNull(trips.getActive())
    }

    @Test
    fun parallelCustodyEventsGetUniqueSequentialSeqAndAValidChain() = runTest {
        val trip = startTrip()
        record(trip.id, CustodyType.PICKUP, "t", "x", "op")
        withContext(Dispatchers.Default) {
            (1..25).map { i -> async { record(trip.id, CustodyType.HANDOFF, "t$i", "x", "op") } }.awaitAll()
        }
        val events = custody.getAll(trip.id)
        assertEquals((1..26).toList(), events.map { it.seq })
        assertEquals(26, events.map { it.hash }.toSet().size)
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(events, signer.publicKey))
    }

    /** Audit F1: a reading stamped before a custody event but stored after it would break that event's readings_digest. */
    @Test
    fun readingAtOrBelowACustodyEventIsRestampedAboveItAndTheDigestStillMatches() = runTest {
        val trip = startTrip()
        val log = LogReading(trips, readings, excursions, clock)
        log(trip.id, Reading(1_000, 1_000, 5.0))
        clock.now = 5_000
        val pickup = record(trip.id, CustodyType.PICKUP, "t", "x", "op")

        log(trip.id, Reading(4_999, 4_999, 5.0)) // captured before the tap, arrived after it
        assertEquals(5_001L, readings.append(trip.id, Reading(5_000, 5_000, 5.0)).tsUtcMs) // same millisecond
        assertEquals(7_000L, readings.append(trip.id, Reading(7_000, 7_000, 5.0)).tsUtcMs) // later readings are untouched
        assertEquals(5_001L, readings.append(trip.id, Reading(1_000, 9_000, 5.0)).tsUtcMs) // wall clock set backwards: kept, not dropped

        val upToTap = readings.getUpTo(trip.id, pickup.tsUtcMs)
        assertEquals(pickup.readingsDigest, com.vassel.frostline.domain.integrity.Canonicalizer.readingsDigest(upToTap))
        assertEquals(5, readings.getAll(trip.id).size) // nothing was dropped
    }

    @Test
    fun failureAfterTheDeliveryInsertRollsEverythingBack() = runTest {
        val trip = startTrip()
        val log = LogReading(trips, readings, excursions, clock)
        log(trip.id, Reading(1_000, 1_000, 9.0)); log(trip.id, Reading(61_000, 61_000, 9.0))
        record(trip.id, CustodyType.PICKUP, "t", "x", "op")
        val failing = object : TripRepository by trips {
            override suspend fun markCompleted(id: String, endedUtcMs: Long) = throw IllegalStateException("boom")
        }
        assertFailsWith<IllegalStateException> { CompleteTrip(record, failing, excursions, tx)(trip.id, "t2", "y", "op") }
        assertEquals(1, custody.getAll(trip.id).size) // DELIVERY insert rolled back
        assertTrue(excursions.getAll(trip.id).single().isOpen) // excursion close rolled back
        assertEquals(TripStatus.ACTIVE, trips.get(trip.id)!!.status)
    }

    @Test
    fun nothingCanBeRecordedAfterDelivery() = runTest {
        val trip = startTrip()
        record(trip.id, CustodyType.PICKUP, "t", "x", "op")
        CompleteTrip(record, trips, excursions, tx)(trip.id, "t2", "y", "op")
        assertFailsWith<IllegalStateException> { record(trip.id, CustodyType.HANDOFF, "t3", "z", "op") }
        assertFailsWith<IllegalStateException> { LogReading(trips, readings, excursions, clock)(trip.id, Reading(1, 1, 5.0)) }
    }
}
