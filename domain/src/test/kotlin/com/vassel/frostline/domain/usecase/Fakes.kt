// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.TripRepository

class FakeTrips : TripRepository {
    val map = linkedMapOf<String, Trip>()
    override suspend fun create(trip: Trip) { map[trip.id] = trip }
    override suspend fun get(id: String) = map[id]
    override suspend fun getActive() = map.values.firstOrNull { it.status == TripStatus.ACTIVE }
    override suspend fun getAll() = map.values.sortedByDescending { it.startedUtcMs }
    override suspend fun markCompleted(id: String, endedUtcMs: Long) {
        map[id] = map.getValue(id).copy(status = TripStatus.COMPLETED, endedUtcMs = endedUtcMs)
    }
}

class FakeReadings : ReadingRepository {
    val map = mutableMapOf<String, MutableList<Reading>>()
    var restamp: (Reading) -> Reading = { it }
    override suspend fun append(tripId: String, reading: Reading): Reading {
        val stored = restamp(reading)
        map.getOrPut(tripId) { mutableListOf() }.add(stored)
        return stored
    }
    override suspend fun getAll(tripId: String) = map[tripId].orEmpty().toList()
    override suspend fun getUpTo(tripId: String, tsUtcMsInclusive: Long) = getAll(tripId).filter { it.tsUtcMs <= tsUtcMsInclusive }
}

class FakeCustody(private val tx: FakeTransactions? = null) : CustodyRepository {
    val list = mutableListOf<CustodyEvent>()
    var appendedInsideTransaction: Boolean? = null
    val diagnostics = mutableListOf<String?>()
    override suspend fun append(event: CustodyEvent, diagnostic: String?) {
        appendedInsideTransaction = (tx?.depth ?: 0) > 0
        list += event
        diagnostics += diagnostic
    }
    override suspend fun getAll(tripId: String) = list.filter { it.tripId == tripId }.sortedBy { it.seq }
    override suspend fun getLast(tripId: String) = getAll(tripId).lastOrNull()
}

class FakeExcursions : ExcursionRepository {
    val map = linkedMapOf<String, Excursion>()
    override suspend fun upsert(excursion: Excursion) { map[excursion.id] = excursion }
    override suspend fun getAll(tripId: String) = map.values.filter { it.tripId == tripId }.sortedBy { it.startUtcMs }
}

class FakeLocation(var result: LocationResult = LocationResult.Fix(GeoLocation(-6.2, 106.816666, 8.0, 0L))) : LocationProvider {
    override suspend fun currentLocation() = result
}

/** Passthrough that counts runs and nesting, for asserting that writes happen inside a transaction. */
class FakeTransactions : com.vassel.frostline.domain.port.TransactionRunner {
    var runs = 0
    var depth = 0
    var maxDepth = 0
    override suspend fun <T> run(block: suspend () -> T): T {
        runs++; depth++; maxDepth = maxOf(maxDepth, depth)
        try { return block() } finally { depth-- }
    }
}
