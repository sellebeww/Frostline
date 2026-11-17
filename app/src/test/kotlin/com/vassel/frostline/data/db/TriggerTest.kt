// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import android.database.sqlite.SQLiteException
import androidx.room.Delete
import androidx.room.Update
import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TriggerTest {
    private lateinit var db: FrostlineDatabase
    private val signer = JcaSigner()

    @Before
    fun setUp() { db = inMemoryDb() }

    @After
    fun tearDown() = db.close()

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    private suspend fun tripWithEvent(): String {
        RoomTripRepository(db).create(trip("t1"))
        RoomCustodyRepository(db).append(
            HashChain.appendEvent(null, CustodyFields("t1", CustodyType.PICKUP, "a", "A", 1, null, null, null, "op", null, false, "d"), signer), null,
        )
        return "t1"
    }

    @Test
    fun allTriggersAreInstalled() {
        val names = mutableListOf<String>()
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertEquals(
            setOf("custody_events_no_update", "custody_events_no_delete", "custody_events_trip_completed", "readings_trip_completed", "trips_single_active", "trips_completed_immutable"),
            names.toSet(),
        )
    }

    @Test
    fun rawUpdateOfCustodyEventIsRejected() = runTest {
        tripWithEvent()
        assertFailsWith<SQLiteException> { sql("UPDATE custody_events SET user_id = 'mallory'") }
        assertFailsWith<SQLiteException> { sql("UPDATE custody_events SET hash = 'x' WHERE seq = 1") }
    }

    @Test
    fun rawDeleteOfCustodyEventIsRejected() = runTest {
        tripWithEvent()
        assertFailsWith<SQLiteException> { sql("DELETE FROM custody_events") }
        assertEquals(1, RoomCustodyRepository(db).getAll("t1").size)
    }

    @Test
    fun custodyDaoHasNoUpdateOrDeleteMethods() {
        val annotated = CustodyDao::class.java.methods.filter { it.isAnnotationPresent(Update::class.java) || it.isAnnotationPresent(Delete::class.java) }
        assertTrue(annotated.isEmpty())
        val rawWrites = CustodyDao::class.java.methods.mapNotNull { it.getAnnotation(androidx.room.Query::class.java)?.value }
            .filter { !it.trimStart().startsWith("SELECT", ignoreCase = true) }
        assertTrue(rawWrites.isEmpty())
    }

    @Test
    fun insertingCustodyIntoACompletedTripIsRejected() = runTest {
        tripWithEvent()
        RoomTripRepository(db).markCompleted("t1", 9_000)
        val late = HashChain.appendEvent(
            RoomCustodyRepository(db).getLast("t1"),
            CustodyFields("t1", CustodyType.HANDOFF, "b", "B", 2, null, null, null, "op", null, false, "d"), signer,
        )
        assertFailsWith<SQLiteException> { RoomCustodyRepository(db).append(late, null) }
    }

    @Test
    fun insertingReadingsIntoACompletedTripIsRejected() = runTest {
        RoomTripRepository(db).create(trip("t1"))
        RoomReadingRepository(db).append("t1", Reading(1, 1, 5.0))
        RoomTripRepository(db).markCompleted("t1", 9_000)
        assertFailsWith<SQLiteException> { RoomReadingRepository(db).append("t1", Reading(2, 2, 5.0)) }
        assertEquals(1, RoomReadingRepository(db).getAll("t1").size)
    }

    @Test
    fun aCompletedTripCannotBeReopenedOrChanged() = runTest {
        RoomTripRepository(db).create(trip("t1"))
        RoomTripRepository(db).markCompleted("t1", 9_000)
        assertFailsWith<SQLiteException> { sql("UPDATE trips SET status = 'ACTIVE', ended_utc_ms = NULL") }
        assertFailsWith<SQLiteException> { sql("UPDATE trips SET operator_id = 'x'") }
        assertEquals(TripStatus.COMPLETED, RoomTripRepository(db).get("t1")!!.status)
    }

    @Test
    fun onlyOneActiveTripIsAllowed() = runTest {
        RoomTripRepository(db).create(trip("t1"))
        assertFailsWith<SQLiteException> { RoomTripRepository(db).create(trip("t2")) }
        RoomTripRepository(db).markCompleted("t1", 9_000)
        RoomTripRepository(db).create(trip("t3"))
        // the failed create rolled back as a whole: no orphan threshold profile from t2
        val c = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM threshold_profiles")
        c.use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
    }
}
