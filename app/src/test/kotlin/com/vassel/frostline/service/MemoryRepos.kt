// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.TripRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

class MemTrips(private val trip: Trip) : TripRepository {
    override suspend fun create(trip: Trip) = Unit
    override suspend fun get(id: String) = trip.takeIf { it.id == id }
    override suspend fun getActive() = trip.takeIf { it.status == TripStatus.ACTIVE }
    override suspend fun markCompleted(id: String, endedUtcMs: Long) = Unit
    override suspend fun getAll() = listOf(trip)
}

class MemReadings : ReadingRepository {
    val list = mutableListOf<Reading>()
    var failOnAppend: Throwable? = null
    override suspend fun append(tripId: String, reading: Reading): Reading {
        failOnAppend?.let { throw it }
        list += reading
        return reading
    }
    override suspend fun getAll(tripId: String) = list.toList()
    override suspend fun getUpTo(tripId: String, tsUtcMsInclusive: Long) = list.filter { it.tsUtcMs <= tsUtcMsInclusive }
}

class MemExcursions : ExcursionRepository {
    val map = linkedMapOf<String, Excursion>()
    override suspend fun upsert(excursion: Excursion) { map[excursion.id] = excursion }
    override suspend fun getAll(tripId: String) = map.values.sortedBy { it.startUtcMs }
}

/** Wall and monotonic time follow the test scheduler's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedClock(private val scheduler: TestCoroutineScheduler, private val baseUtcMs: Long = BASE) : Clock {
    override fun nowUtcMs() = baseUtcMs + scheduler.currentTime
    override fun elapsedRealtimeMs() = scheduler.currentTime

    companion object {
        const val BASE = 1_700_000_000_000L
    }
}
