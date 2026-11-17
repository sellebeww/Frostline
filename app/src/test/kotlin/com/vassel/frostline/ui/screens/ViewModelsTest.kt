// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.vassel.frostline.data.ble.FakeSensorAdapter
import com.vassel.frostline.data.ble.SimScenarios
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.SensorAdapter
import com.vassel.frostline.domain.usecase.LogReading
import com.vassel.frostline.service.MemExcursions
import com.vassel.frostline.service.MemReadings
import com.vassel.frostline.service.MemTrips
import com.vassel.frostline.service.MonitorLoop
import com.vassel.frostline.service.MonitorStatus
import com.vassel.frostline.service.MonitorStatusStore
import com.vassel.frostline.service.SchedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    @After fun resetMain() = Dispatchers.resetMain()

    private fun vmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) {
        val d = UnconfinedTestDispatcher()
        Dispatchers.setMain(d)
        runTest(d) { block() }
    }

    // ---- Home: one test per simulator scenario -------------------------------------------------------------------

    private inner class HomeFixture(scope: kotlinx.coroutines.test.TestScope, hasTrip: Boolean = true) {
        val clock = SchedClock(scope.testScheduler)
        val trip = Trip("t1", null, "user-1", SchedClock.BASE, null, TripStatus.ACTIVE, 60, ThresholdProfile(), sensorId = "s1")
        val trips = MemTrips(trip)
        val readings = MemReadings()
        val excursions = MemExcursions()
        val store = MonitorStatusStore()
        val monitor = FakeMonitor()
        val vm = HomeViewModel(MemSensors(Sensor("s1", "abc123", "Sensor uji", "AA")), if (hasTrip) trips else EmptyTrips(), readings, excursions, FakeOperators(), store, monitor, clock)

        fun run(scope: kotlinx.coroutines.test.TestScope, adapter: SensorAdapter) {
            store.state.value = MonitorStatus(running = true, tripId = "t1")
            scope.backgroundScope.launch {
                MonitorLoop("t1", 60_000, adapter, LogReading(trips, readings, excursions, clock), clock, { }, store.state).run()
            }
        }
    }

    private class EmptyTrips : com.vassel.frostline.domain.port.TripRepository {
        override suspend fun create(trip: Trip) = Unit
        override suspend fun get(id: String): Trip? = null
        override suspend fun getActive(): Trip? = null
        override suspend fun markCompleted(id: String, endedUtcMs: Long) = Unit
        override suspend fun getAll() = emptyList<Trip>()
    }

    private suspend fun ReceiveTurbine<HomeUiState>.awaitStatus(target: HomeStatus): HomeUiState {
        repeat(600) { val s = awaitItem(); if (s.status == target) return s }
        fail("never reached $target")
    }

    @Test fun noTripShowsReady() = vmTest { val s = this;
        val f = HomeFixture(s, hasTrip = false)
        f.vm.state.test { assertEquals(HomeStatus.READY, awaitStatus(HomeStatus.READY).status); cancelAndIgnoreRemainingEvents() }
    }

    @Test fun anActiveTripWithoutTheServiceShowsIdle() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test { assertTrue(awaitStatus(HomeStatus.IDLE).hasActiveTrip); cancelAndIgnoreRemainingEvents() }
    }

    @Test fun stableScenarioStaysSafeWithMktNearFive() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test {
            f.run(s, FakeSensorAdapter(SimScenarios.stable))
            advanceTimeBy(25 * 60_000L)
            runCurrent()
            val items = cancelAndConsumeRemainingEvents().filterIsInstance<app.cash.turbine.Event.Item<HomeUiState>>().map { it.value }
            assertTrue(items.none { it.status == HomeStatus.HEAT || it.status == HomeStatus.COLD || it.status == HomeStatus.FREEZE })
            val last = items.last { it.status == HomeStatus.SAFE }
            assertEquals(5.0, last.tempC)
            assertEquals(5.0, assertNotNull(last.mktC), 0.01)
            assertEquals(0L, last.outOfRangeMs)
        }
    }

    @Test fun heatScenarioShowsHeatWithAnOpenExcursionBanner() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test {
            f.run(s, FakeSensorAdapter(SimScenarios.heat))
            val s = awaitStatus(HomeStatus.HEAT)
            assertEquals(ExcursionType.HEAT, s.openExcursion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun freezeScenarioReachesFreezeRisk() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test {
            f.run(s, FakeSensorAdapter(SimScenarios.freeze))
            assertEquals(ExcursionType.FREEZE, awaitStatus(HomeStatus.FREEZE).openExcursion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dropoutScenarioShowsSensorLostThenRecovers() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test {
            f.run(s, FakeSensorAdapter(SimScenarios.dropout))
            assertTrue(awaitStatus(HomeStatus.SENSOR_LOST).sensorLost)
            assertFalse(awaitStatus(HomeStatus.SAFE).sensorLost)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun oscillationAroundTheLimitKeepsOneBannerOpen() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.vm.state.test {
            f.run(s, FakeSensorAdapter(SimScenarios.oscillate))
            advanceTimeBy(25 * 60_000L)
            runCurrent()
            val items = cancelAndConsumeRemainingEvents().filterIsInstance<app.cash.turbine.Event.Item<HomeUiState>>().map { it.value }
            assertEquals(1, f.excursions.map.size)
            assertEquals(ExcursionType.HEAT, items.last().openExcursion) // stays until it closes
        }
    }

    @Test fun resumeAskedWhileAndroidRefusesEmitsAOneShotEvent() = vmTest { val s = this;
        val f = HomeFixture(s)
        f.monitor.startResult = false
        f.vm.events.test {
            f.vm.resume()
            assertEquals(HomeEvent.StartBlocked, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        f.vm.stop()
        assertEquals(1, f.monitor.stops)
    }

    // ---- Settings -------------------------------------------------------------------------------------------------

    @Test fun settingsShowTheStoredThresholdsAndValidateWhileTyping() = vmTest { val s = this;
        val repo = MemThresholds(ThresholdProfile(2.0, 8.0, 0.0))
        val vm = SettingsViewModel(repo, FakeSettings())
        vm.state.test {
            val first = awaitItem()
            val loaded = if (first.loaded) first else awaitItem()
            assertEquals("2,0", loaded.minText); assertEquals("8,0", loaded.maxText); assertEquals("0,0", loaded.freezeText)
            vm.onMin("9")
            assertEquals(setOf(ThresholdError.MIN_NOT_BELOW_MAX), awaitItem().errors)
            vm.onMin("2,5")
            assertTrue(awaitItem().errors.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun anInvalidFormIsNeverSavedAndAValidOneIs() = vmTest { val s = this;
        val repo = MemThresholds()
        val vm = SettingsViewModel(repo, FakeSettings())
        vm.onFreeze("3")
        vm.save()
        assertEquals(0, repo.saves)
        assertEquals(setOf(ThresholdError.FREEZE_NOT_BELOW_MIN), vm.state.value.errors)
        vm.onFreeze("-1")
        vm.onMax("9,5")
        vm.save()
        assertEquals(1, repo.saves)
        assertEquals(ThresholdProfile(2.0, 9.5, -1.0), repo.profile)
        assertTrue(vm.state.value.saved)
        vm.onMin("2,1")
        assertFalse(vm.state.value.saved) // editing clears the confirmation
    }

    @Test fun theIntervalIsClampedAndResetRestoresDefaults() = vmTest { val s = this;
        val settings = FakeSettings()
        val vm = SettingsViewModel(MemThresholds(ThresholdProfile(1.0, 9.0, -1.0)), settings)
        vm.setInterval(5); assertEquals(10, vm.state.value.intervalSec)
        vm.setInterval(5000); assertEquals(600, vm.state.value.intervalSec)
        vm.resetDefaults()
        assertEquals("2,0", vm.state.value.minText)
        assertEquals("8,0", vm.state.value.maxText)
    }

    // ---- Start trip -----------------------------------------------------------------------------------------------

    private fun startVm(sensor: Sensor? = Sensor("s1", "h", "S", "AA"), operator: Boolean = true, monitor: FakeMonitor = FakeMonitor(), trips: WritableTrips = WritableTrips()) =
        StartTripViewModel(MemCarriers(), MemThresholds(), MemSensors(sensor), trips, FakeOperators(if (operator) com.vassel.frostline.data.OperatorProfile("user-1", "Sari", "P") else null), FakeSettings(logIntervalSec = 120), monitor, com.vassel.frostline.data.db.TestClock())

    @Test fun startingWithoutASensorOrOperatorShowsAnError() = vmTest { val s = this;
        val noSensor = startVm(sensor = null)
        noSensor.start(); assertEquals(StartTripError.NO_SENSOR, noSensor.state.value.error)
        val noOperator = startVm(operator = false)
        noOperator.start(); assertEquals(StartTripError.NO_OPERATOR, noOperator.state.value.error)
    }

    @Test fun startingCreatesTheTripWithTheChosenSettingsAndStartsMonitoring() = vmTest { val s = this;
        val trips = WritableTrips(); val monitor = FakeMonitor()
        val vm = startVm(monitor = monitor, trips = trips)
        vm.onNewCarrierName("Kotak A"); vm.setInterval(30)
        vm.events.test {
            vm.start()
            assertEquals(StartTripEvent.Started(serviceBlocked = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val t = trips.list.single()
        assertEquals(30, t.logIntervalSec); assertEquals("s1", t.sensorId); assertEquals("user-1", t.operatorId); assertEquals("c1", t.carrierId)
        assertEquals(1, monitor.starts)
    }

    @Test fun theIntervalDefaultsFromSettingsAndIsClamped() = vmTest { val s = this;
        val vm = startVm()
        assertEquals(120, vm.state.value.intervalSec)
        vm.setInterval(1); assertEquals(10, vm.state.value.intervalSec)
        vm.setInterval(9999); assertEquals(600, vm.state.value.intervalSec)
    }

    @Test fun aRefusedServiceStartStillCreatesTheTripAndTellsTheScreen() = vmTest { val s = this;
        val vm = startVm(monitor = FakeMonitor(startResult = false))
        vm.events.test { vm.start(); assertEquals(StartTripEvent.Started(serviceBlocked = true), awaitItem()); cancelAndIgnoreRemainingEvents() }
    }

    @Test fun aSecondTripWhileOneIsActiveIsRefused() = vmTest { val s = this;
        val trips = WritableTrips()
        val vm = startVm(trips = trips)
        vm.events.test { vm.start(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.start()
        assertEquals(StartTripError.TRIP_ACTIVE, vm.state.value.error)
        assertEquals(1, trips.list.size)
        assertNull(null)
    }
}
