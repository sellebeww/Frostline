// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.engine.TripStatsCalculator
import com.vassel.frostline.domain.FakeClock
import com.vassel.frostline.domain.integrity.Canonicalizer
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.JcaSigner
import com.vassel.frostline.domain.integrity.newKeyPair
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.report.ReportSample
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportDataTest {
    private class Carriers(val list: List<Carrier>) : CarrierRepository {
        override suspend fun getAll() = list
        override suspend fun create(name: String, capacityLiters: Double?) = error("unused")
    }

    private class Sensors(val sensor: Sensor?) : SensorRepository {
        override suspend fun pair(address: String, model: String?, nowUtcMs: Long) = error("unused")
        override suspend fun getPaired() = sensor
        override suspend fun get(id: String) = sensor?.takeIf { it.id == id }
    }

    private val pair = newKeyPair()
    private val trips = FakeTrips().apply { map[ReportSample.TRIP_ID] = ReportSample.trip }
    private val readings = FakeReadings().apply { map[ReportSample.TRIP_ID] = ReportSample.readings.toMutableList() }
    private val excursions = FakeExcursions().apply { ReportSample.excursions.forEach { map[it.id] = it } }
    private val custody = FakeCustody().apply { list += ReportSample.custody(JcaSigner(pair)) }

    private fun build(carriers: List<Carrier>, sensor: Sensor?) =
        BuildReportData(trips, readings, excursions, custody, carriers = Carriers(carriers), sensors = Sensors(sensor), clock = FakeClock(0L))

    @Test
    fun statsEqualTheStatsCalculatorOutput() = runTest {
        val data = build(emptyList(), null)(ReportSample.TRIP_ID, pair.public)
        val expected = TripStatsCalculator.compute(ReportSample.readings, ReportSample.excursions, 60, ReportSample.trip.endedUtcMs!!)
        assertEquals(expected, data.stats)
        assertEquals(ChainVerification.Valid, data.chainVerification)
        assertEquals(Canonicalizer.readingsDigest(ReportSample.readings), data.readingsDigest)
    }

    @Test
    fun headerCarriesCarrierAndOnlyTheHashOfTheSensorAddress() = runTest {
        val sensor = Sensor("sensor-1", "0123456789ab", "ESP32-ESS", "AA:BB:CC:DD:EE:FF")
        val data = build(listOf(Carrier("carrier-1", "Kotak A", 2.0, null)), sensor)(ReportSample.TRIP_ID, pair.public)
        assertEquals("Kotak A", data.carrier?.name)
        assertEquals("0123456789ab", data.sensorMacHash)
        assertEquals("ESP32-ESS", data.sensorModel)
        assertEquals(false, data.toString().contains("AA:BB:CC"), "the raw MAC must never reach report data")
    }

    @Test
    fun missingCarrierAndSensorAreNullNotErrors() = runTest {
        val data = build(emptyList(), null)(ReportSample.TRIP_ID, pair.public)
        assertNull(data.carrier)
        assertNull(data.sensorMacHash)
    }
}
