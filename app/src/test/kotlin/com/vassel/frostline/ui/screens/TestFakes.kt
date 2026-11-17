// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.data.OperatorProfile
import com.vassel.frostline.data.OperatorStore
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.ThresholdRepository
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.service.MonitorController

class FakeOperators(var profile: OperatorProfile? = OperatorProfile("user-1", "Sari", "Petugas")) : OperatorStore {
    override fun get() = profile
    override fun save(name: String, role: String): OperatorProfile? =
        name.trim().takeIf { it.isNotEmpty() }?.let { OperatorProfile(profile?.userId ?: "user-1", it, role) }?.also { profile = it }
}

class FakeSettings(logIntervalSec: Int = 60, override var onboardingDone: Boolean = false) : AppSettings {
    override var logIntervalSec: Int = logIntervalSec.coerceIn(AppSettings.MIN_INTERVAL_SEC, AppSettings.MAX_INTERVAL_SEC)
        set(value) { field = value.coerceIn(AppSettings.MIN_INTERVAL_SEC, AppSettings.MAX_INTERVAL_SEC) }
}

class FakeMonitor(var startResult: Boolean = true) : MonitorController {
    var starts = 0
    var stops = 0
    override fun start(): Boolean { starts++; return startResult }
    override fun stop() { stops++ }
}

class MemSensors(var paired: Sensor? = null) : SensorRepository {
    override suspend fun pair(address: String, model: String?, nowUtcMs: Long) = Sensor("s1", "hash", model, address).also { paired = it }
    override suspend fun getPaired() = paired
    override suspend fun get(id: String) = paired?.takeIf { it.id == id }
}

class MemThresholds(var profile: ThresholdProfile = ThresholdProfile()) : ThresholdRepository {
    var saves = 0
    override suspend fun getDefault() = profile
    override suspend fun saveDefault(profile: ThresholdProfile) { saves++; this.profile = profile }
}

class MemCarriers : CarrierRepository {
    val list = mutableListOf<Carrier>()
    override suspend fun getAll() = list.toList()
    override suspend fun create(name: String, capacityLiters: Double?) = Carrier("c${list.size + 1}", name.trim(), capacityLiters, null).also { list += it }
}

/** A writable trip store (the one in service tests holds a single fixed trip). */
class WritableTrips : TripRepository {
    val list = mutableListOf<Trip>()
    override suspend fun create(trip: Trip) { list += trip }
    override suspend fun get(id: String) = list.firstOrNull { it.id == id }
    override suspend fun getActive() = list.firstOrNull { it.status == TripStatus.ACTIVE }
    override suspend fun markCompleted(id: String, endedUtcMs: Long) = Unit
    override suspend fun getAll() = list.sortedByDescending { it.startedUtcMs }
}
