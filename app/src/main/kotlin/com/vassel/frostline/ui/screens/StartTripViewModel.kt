// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.data.OperatorStore
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.ThresholdRepository
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.usecase.StartTrip
import com.vassel.frostline.service.MonitorController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StartTripError { NO_SENSOR, NO_OPERATOR, TRIP_ACTIVE, INTERVAL, CARRIER_NAME_TOO_LONG }

sealed interface StartTripEvent {
    /** The trip exists. [serviceBlocked] is true when Android refused to start the service (it can be resumed from Home). */
    data class Started(val serviceBlocked: Boolean) : StartTripEvent
}

data class StartTripUiState(
    val carriers: List<Carrier> = emptyList(),
    val selectedCarrierId: String? = null,
    val newCarrierName: String = "",
    val thresholds: ThresholdProfile = ThresholdProfile(),
    val sensor: Sensor? = null,
    val intervalSec: Int = AppSettings.DEFAULT_INTERVAL_SEC,
    val busy: Boolean = false,
    val error: StartTripError? = null,
)

@HiltViewModel
class StartTripViewModel @Inject constructor(
    private val carriers: CarrierRepository,
    private val thresholds: ThresholdRepository,
    private val sensors: SensorRepository,
    private val trips: TripRepository,
    private val operators: OperatorStore,
    private val settings: AppSettings,
    private val monitor: MonitorController,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(StartTripUiState(intervalSec = settings.logIntervalSec))
    val state: StateFlow<StartTripUiState> = _state.asStateFlow()
    private val _events = Channel<StartTripEvent>(Channel.BUFFERED)
    val events: Flow<StartTripEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(carriers = carriers.getAll(), thresholds = thresholds.getDefault(), sensor = sensors.getPaired()) }
        }
    }

    fun selectCarrier(id: String?) = _state.update { it.copy(selectedCarrierId = id, newCarrierName = if (id != null) "" else it.newCarrierName, error = null) }

    fun onNewCarrierName(text: String) = _state.update { it.copy(newCarrierName = text.take(MAX_NAME), selectedCarrierId = null, error = null) }

    fun setInterval(seconds: Int) = _state.update { it.copy(intervalSec = seconds.coerceIn(AppSettings.MIN_INTERVAL_SEC, AppSettings.MAX_INTERVAL_SEC), error = null) }

    /**
     * Creates the trip and starts monitoring. The screen asks for permissions before calling this. The sensor and the
     * operator are read again here, so a sensor that was just picked is never missed because the screen has not refreshed.
     */
    fun start() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val s = _state.value
                val sensor = sensors.getPaired()
                val operator = operators.get()
                val error = when {
                    sensor == null -> StartTripError.NO_SENSOR
                    operator == null -> StartTripError.NO_OPERATOR
                    s.intervalSec !in AppSettings.MIN_INTERVAL_SEC..AppSettings.MAX_INTERVAL_SEC -> StartTripError.INTERVAL
                    else -> null
                }
                if (error != null || sensor == null || operator == null) {
                    _state.update { it.copy(error = error, sensor = sensor) }
                    return@launch
                }
                val carrierId = if (s.newCarrierName.isNotBlank()) carriers.create(s.newCarrierName, null).id else s.selectedCarrierId
                StartTrip(trips, clock)(carrierId, operator.userId, s.intervalSec, thresholds.getDefault(), sensor.id)
                _events.send(StartTripEvent.Started(serviceBlocked = !monitor.start()))
            } catch (_: IllegalStateException) {
                _state.update { it.copy(error = StartTripError.TRIP_ACTIVE) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
