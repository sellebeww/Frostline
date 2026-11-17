// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.ble.ScanProblem
import com.vassel.frostline.data.ble.ScannedSensor
import com.vassel.frostline.data.ble.SensorBackend
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ScanUiState(
    val scanning: Boolean = false,
    val sensors: List<ScannedSensor> = emptyList(),
    val problem: ScanProblem? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val backend: SensorBackend,
    private val sensors: SensorRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Scans for [SCAN_LIMIT_MS] and then stops, to save battery. The scan also stops when the screen is left. */
    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = ScanUiState(scanning = true)
            try {
                withTimeoutOrNull(SCAN_LIMIT_MS) {
                    backend.scan().collect { update -> _state.update { it.copy(sensors = update.sensors, problem = update.problem) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { // for example SecurityException when the permission is revoked mid-scan
                _state.update { it.copy(problem = ScanProblem.PERMISSION) }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun pair(sensor: ScannedSensor, onDone: () -> Unit) {
        viewModelScope.launch {
            sensors.pair(sensor.address, sensor.name, clock.nowUtcMs())
            onDone()
        }
    }

    private companion object {
        const val SCAN_LIMIT_MS = 60_000L
    }
}
