// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import com.vassel.frostline.data.ble.FakeSensorAdapter
import com.vassel.frostline.data.ble.SimScenarios
import com.vassel.frostline.data.ble.SimStep
import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.SensorAdapter
import com.vassel.frostline.domain.port.SensorEvent
import com.vassel.frostline.domain.port.SensorFailure
import com.vassel.frostline.domain.usecase.LogReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorLoopTest {
    private val min = 60_000L
    private val trip = Trip(
        "t1", null, "op", SchedClock.BASE, null, TripStatus.ACTIVE, logIntervalSec = 60,
        thresholds = ThresholdProfile(), sensorId = "s1",
    )
    private val trips = MemTrips(trip)
    private val readings = MemReadings()
    private val excursions = MemExcursions()
    private val events = mutableListOf<AlarmEvent>()
    private val status = MutableStateFlow(MonitorStatus(running = true, tripId = "t1"))

    private inline fun <reified T : AlarmEvent> count() = events.count { it is T }

    private fun TestScope.loop(adapter: SensorAdapter, intervalMs: Long = 60_000, log: LogReading? = null): MonitorLoop {
        val clock = SchedClock(testScheduler)
        return MonitorLoop("t1", intervalMs, adapter, log ?: LogReading(trips, readings, excursions, clock), clock, { events += it }, status)
    }

    private fun TestScope.runToEnd(adapter: SensorAdapter, intervalMs: Long = 60_000, log: LogReading? = null) {
        val job = launch { loop(adapter, intervalMs, log).run() }
        advanceUntilIdle()
        assertTrue(job.isCompleted)
    }

    @Test
    fun samplesAreThrottledToTheLogInterval() = runTest {
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(5.0, 5 * min))))
        assertEquals(5, readings.list.size) // 30 samples at 10 s, one logged per 60 s
        assertEquals(listOf(0L, 60_000, 120_000, 180_000, 240_000), readings.list.map { it.elapsedMs })
        assertEquals(5.0, status.value.lastTempC)
    }

    @Test
    fun readingsCarryWallClockAndElapsedTime() = runTest {
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(5.0, 2 * min))))
        assertEquals(SchedClock.BASE, readings.list.first().tsUtcMs)
        assertEquals(SchedClock.BASE + 60_000, readings.list[1].tsUtcMs)
        assertEquals(60_000L, readings.list[1].elapsedMs)
    }

    @Test
    fun heatAlarmFiresOncePerExcursionAndClosesOnce() = runTest {
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(5.0, 2 * min), SimStep.Hold(9.0, 10 * min), SimStep.Hold(5.0, 5 * min))))
        assertEquals(1, count<AlarmEvent.ExcursionOpened>())
        assertEquals(1, count<AlarmEvent.ExcursionClosed>())
        assertEquals(0, count<AlarmEvent.FreezeRisk>())
        val ex = excursions.map.values.single()
        assertEquals(ExcursionType.HEAT, ex.type)
        assertFalse(ex.isOpen)
    }

    @Test
    fun freezeRiskFiresOnceWhileColdOpensOnce() = runTest {
        runToEnd(FakeSensorAdapter(SimScenarios.freeze))
        assertEquals(1, count<AlarmEvent.ExcursionOpened>())
        assertEquals(1, count<AlarmEvent.FreezeRisk>())
        assertEquals(ExcursionType.FREEZE, excursions.map.values.single().type)
    }

    @Test
    fun oscillatingAroundTheLimitDoesNotRepeatAlarms() = runTest {
        // 8.0 +- 0.15 never reaches the 7.8 close band, so hysteresis keeps one open excursion and one alarm
        runToEnd(FakeSensorAdapter(listOf(SimStep.Oscillate(8.0, 0.15, min, 20 * min))), intervalMs = 10_000)
        assertEquals(1, count<AlarmEvent.ExcursionOpened>())
        assertEquals(0, count<AlarmEvent.ExcursionClosed>())
        assertEquals(1, excursions.map.size)
    }

    @Test
    fun sensorLostAndRecoveredEachFireOnce() = runTest {
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(5.0, 2 * min), SimStep.Silence(10 * min), SimStep.Hold(5.0, 2 * min))))
        assertEquals(1, count<AlarmEvent.SensorLost>())
        assertEquals(1, count<AlarmEvent.SensorRecovered>())
        val names = events.map { it::class.simpleName }
        assertTrue(names.indexOf("SensorLost") < names.indexOf("SensorRecovered"))
        assertFalse(status.value.sensorLost)
        assertEquals(ConnectionState.CONNECTED, status.value.connection)
    }

    @Test
    fun watchdogReportsLossWhileTheSensorIsStillSilent() = runTest {
        val job = launch { loop(FakeSensorAdapter(listOf(SimStep.Hold(5.0, min), SimStep.Silence(30 * min)))).run() }
        advanceTimeBy(5 * min)
        assertEquals(0, count<AlarmEvent.SensorLost>())
        advanceTimeBy(2 * min) // the last reading was at 50 s, the 300 s threshold passes at about 350 s
        assertEquals(1, count<AlarmEvent.SensorLost>())
        assertTrue(status.value.sensorLost)
        job.cancel()
    }

    @Test
    fun theFullDemoScenarioProducesTheExpectedAlarms() = runTest {
        runToEnd(FakeSensorAdapter(SimScenarios.all), intervalMs = 60_000)
        assertTrue(count<AlarmEvent.ExcursionOpened>() >= 3) // heat, cold, then the oscillation
        assertEquals(1, count<AlarmEvent.FreezeRisk>())
        assertEquals(1, count<AlarmEvent.SensorLost>())
        assertEquals(1, count<AlarmEvent.SensorRecovered>())
    }

    @Test
    fun stateIsRebuiltFromHistoryAfterARestart() = runTest {
        // first run: the process "dies" while an excursion is open
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(9.0, 3 * min))))
        assertEquals(1, count<AlarmEvent.ExcursionOpened>())
        val opened = excursions.map.values.single()
        assertTrue(opened.isOpen)
        // second run: a new LogReading instance, no memory of the first one
        runToEnd(FakeSensorAdapter(listOf(SimStep.Hold(5.0, 3 * min))))
        assertEquals(1, count<AlarmEvent.ExcursionOpened>(), "no second alarm for the same excursion")
        assertEquals(1, count<AlarmEvent.ExcursionClosed>())
        val ex = excursions.map.values.single()
        assertEquals(opened.startUtcMs, ex.startUtcMs)
        assertFalse(ex.isOpen)
    }

    @Test
    fun cancellingStopsTheSensorFlowAndTheWatchdog() = runTest {
        var active = 0
        val counted = object : SensorAdapter {
            override fun events(): Flow<SensorEvent> =
                FakeSensorAdapter(listOf(SimStep.Hold(5.0, 60 * min))).events().onStart { active++ }.onCompletion { active-- }
        }
        val job = launch { loop(counted).run() }
        advanceTimeBy(5 * min)
        assertEquals(1, active)
        job.cancel()
        advanceUntilIdle()
        assertEquals(0, active)
        val logged = readings.list.size
        advanceTimeBy(30 * min)
        assertEquals(logged, readings.list.size)
        assertEquals(0, count<AlarmEvent.SensorLost>()) // the watchdog died with the loop
    }

    @Test
    fun aSensorFailureIsShownAndEndsTheLoop() = runTest {
        val failing = object : SensorAdapter {
            override fun events(): Flow<SensorEvent> = flow {
                emit(SensorEvent.Connection(ConnectionState.CONNECTING))
                emit(SensorEvent.Failure(SensorFailure.NO_ESS_SERVICE))
            }
        }
        runToEnd(failing)
        assertEquals(SensorFailure.NO_ESS_SERVICE, status.value.failure)
        assertEquals(0, readings.list.size)
    }

    @Test
    fun aDatabaseFailureIsNotSwallowed() = runTest {
        readings.failOnAppend = IllegalStateException("disk full")
        assertFailsWith<IllegalStateException> { loop(FakeSensorAdapter(SimScenarios.stable)).run() }
    }
}
