// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import android.database.sqlite.SQLiteException
import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.TripStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomRepositoriesTest {
    private lateinit var db: FrostlineDatabase
    private lateinit var trips: RoomTripRepository
    private lateinit var readings: RoomReadingRepository
    private lateinit var excursions: RoomExcursionRepository
    private lateinit var custody: RoomCustodyRepository

    @Before
    fun setUp() {
        db = inMemoryDb()
        trips = RoomTripRepository(db)
        readings = RoomReadingRepository(db)
        excursions = RoomExcursionRepository(db)
        custody = RoomCustodyRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tripRoundTripsWithItsThresholdSnapshot() = runTest {
        db.carrierDao().insert(CarrierEntity("box-1", "Kotak 1", 5.0, null))
        val t = trip("t1", carrierId = "box-1").copy(thresholds = ThresholdProfile(1.0, 9.0, -1.0), logIntervalSec = 120)
        trips.create(t)
        assertEquals(t, trips.get("t1"))
        assertEquals(t, trips.getActive())
        assertNull(trips.get("missing"))
    }

    @Test
    fun markCompletedSetsStatusAndRejectsASecondCompletion() = runTest {
        trips.create(trip("t1"))
        trips.markCompleted("t1", 5_000L)
        val done = trips.get("t1")!!
        assertEquals(TripStatus.COMPLETED, done.status)
        assertEquals(5_000L, done.endedUtcMs)
        assertNull(trips.getActive())
        assertFailsWith<IllegalStateException> { trips.markCompleted("t1", 6_000L) }
        assertFailsWith<IllegalStateException> { trips.markCompleted("nope", 6_000L) }
    }

    @Test
    fun readingsComeBackInCaptureOrderAndUpToFilterWorks() = runTest {
        trips.create(trip("t1"))
        val a = Reading(3_000, 10, 5.0)
        val b = Reading(1_000, 20, 6.0, 55.0) // wall clock went backwards
        val c = Reading(2_000, 30, 7.0)
        listOf(a, b, c).forEach { readings.append("t1", it) }
        assertEquals(listOf(a, b, c), readings.getAll("t1"))
        assertEquals(listOf(b, c), readings.getUpTo("t1", 2_000))
    }

    @Test
    fun readingForUnknownTripIsRejectedByForeignKey() = runTest {
        assertFailsWith<SQLiteException> { readings.append("ghost", Reading(1, 1, 5.0)) }
    }

    @Test
    fun excursionUpsertKeepsTheRowAndUpdatesIt() = runTest {
        trips.create(trip("t1"))
        excursions.upsert(Excursion("t1", ExcursionType.COLD, 1_000, null, 1.5))
        val firstId = db.excursionDao().find("t1", 1_000)!!.id
        excursions.upsert(Excursion("t1", ExcursionType.FREEZE, 1_000, 9_000, -0.5))
        excursions.upsert(Excursion("t1", ExcursionType.HEAT, 20_000, null, 9.0))
        val all = excursions.getAll("t1")
        assertEquals(listOf(ExcursionType.FREEZE, ExcursionType.HEAT), all.map { it.type })
        assertEquals(9_000L, all[0].endUtcMs)
        assertEquals(-0.5, all[0].peakC)
        assertEquals(firstId, db.excursionDao().find("t1", 1_000)!!.id)
    }

    @Test
    fun custodyEventRoundTripsThroughTheMapper() = runTest {
        trips.create(trip("t1"))
        val f = CustodyFields("t1", CustodyType.PICKUP, "04aa", "Gudang", 1_500, -6.2, 106.8, 7.5, "op", 523, true, "d".repeat(64))
        val event = HashChain.appendEvent(null, f, JcaSigner())
        custody.append(event, null)
        assertEquals(listOf(event), custody.getAll("t1"))
        assertEquals(event, custody.getLast("t1"))
        assertNull(custody.getLast("other"))
    }

    @Test
    fun duplicateSequenceNumberIsRejected() = runTest {
        trips.create(trip("t1"))
        val signer = JcaSigner()
        val first = HashChain.appendEvent(null, CustodyFields("t1", CustodyType.PICKUP, null, null, 1, null, null, null, "op", null, false, "d"), signer)
        custody.append(first, null)
        assertFailsWith<SQLiteException> { custody.append(first, null) }
    }
}
