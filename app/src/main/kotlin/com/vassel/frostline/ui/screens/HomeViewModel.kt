// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.OperatorProfile
import com.vassel.frostline.data.OperatorStore
import com.vassel.frostline.domain.engine.TripStatsCalculator
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.SensorFailure
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.service.MonitorController
import com.vassel.frostline.service.MonitorStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot things the screen shows once (a snackbar), not state. */
sealed interface HomeEvent {
    data object StartBlocked : HomeEvent
}

data class HomeUiState(
    val status: HomeStatus = HomeStatus.READY,
    val tempC: Double? = null,
    val mktC: Double? = null,
    val outOfRangeMs: Long = 0,
    val sensor: Sensor? = null,
    val operator: OperatorProfile? = null,
    val hasActiveTrip: Boolean = false,
    val running: Boolean = false,
    val sensorLost: Boolean = false,
    val openExcursion: ExcursionType? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val failure: SensorFailure? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sensors: SensorRepository,
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val excursions: ExcursionRepository,
    private val operators: OperatorStore,
    private val store: MonitorStatusStore,
    private val monitor: MonitorController,
    private val clock: Clock,
) : ViewModel() {
    private data class Snapshot(
        val sensor: Sensor? = null,
        val operator: OperatorProfile? = null,
        val hasActiveTrip: Boolean = false,
        val open: ExcursionType? = null,
        val mktC: Double? = null,
        val outOfRangeMs: Long = 0,
        val lastTempC: Double? = null,
    )

    private val snapshot = MutableStateFlow(Snapshot())
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** A single UI state: the stored snapshot of the trip combined with the live service status. */
    val state: StateFlow<HomeUiState> = combine(snapshot, store.status) { snap, live ->
        HomeUiState(
            status = homeStatus(snap.hasActiveTrip, live.running, live.sensorLost, snap.open),
            tempC = live.lastTempC ?: snap.lastTempC,
            mktC = snap.mktC,
            outOfRangeMs = snap.outOfRangeMs,
            sensor = snap.sensor,
            operator = snap.operator,
            hasActiveTrip = snap.hasActiveTrip,
            running = live.running,
            sensorLost = live.sensorLost,
            openExcursion = snap.open,
            connection = live.connection,
            failure = live.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Reload the stored snapshot whenever a reading was logged or the service started, stopped or lost the sensor.
        viewModelScope.launch {
            store.status.map { Triple(it.running, it.lastReadingUtcMs, it.sensorLost) }.distinctUntilChanged().collect { load() }
        }
    }

    /** Called when the screen becomes visible again. */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val trip = trips.getActive()
        val sensor = (trip?.sensorId?.let { sensors.get(it) }) ?: sensors.getPaired()
        var open: ExcursionType? = null
        var mkt: Double? = null
        var outOfRange = 0L
        var lastTemp: Double? = null
        if (trip != null) {
            val rs = readings.getAll(trip.id)
            val ex = excursions.getAll(trip.id)
            open = ex.firstOrNull { it.isOpen }?.type
            val stats = TripStatsCalculator.compute(rs, ex, trip.logIntervalSec, clock.nowUtcMs())
            mkt = stats.mktC
            outOfRange = stats.outOfRangeMs
            lastTemp = rs.lastOrNull()?.tempC
        }
        snapshot.value = Snapshot(sensor, operators.get(), trip != null, open, mkt, outOfRange, lastTemp)
    }

    fun resume() {
        if (!monitor.start()) _events.trySend(HomeEvent.StartBlocked)
    }

    fun stop() = monitor.stop()
}
