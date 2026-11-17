// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.security.KeystoreSigner
import com.vassel.frostline.domain.engine.TripStatsCalculator
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStats
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.timeline.TimelineItem
import com.vassel.frostline.domain.timeline.TripTimeline
import com.vassel.frostline.domain.usecase.BuildReportData
import com.vassel.frostline.domain.usecase.ReportData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TripListItem(val trip: Trip, val stats: TripStats, val carrierName: String?)

data class TripListUiState(val items: List<TripListItem> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val excursions: ExcursionRepository,
    private val carriers: CarrierRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(TripListUiState())
    val state: StateFlow<TripListUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val names = carriers.getAll().associate { it.id to it.name }
            val items = trips.getAll().map { t ->
                TripListItem(t, TripStatsCalculator.compute(readings.getAll(t.id), excursions.getAll(t.id), t.logIntervalSec, t.endedUtcMs ?: clock.nowUtcMs()), t.carrierId?.let(names::get))
            }
            _state.value = TripListUiState(items, loading = false)
        }
    }
}

data class TripDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val report: ReportData? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val alarms: List<TimelineItem> = emptyList(),
    val gapThresholdMs: Long = 300_000,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    trips: TripRepository,
    readings: ReadingRepository,
    excursions: ExcursionRepository,
    custody: CustodyRepository,
    carriers: CarrierRepository,
    sensors: SensorRepository,
    clock: Clock,
    private val signer: KeystoreSigner,
) : ViewModel() {
    private val tripId: String = checkNotNull(savedState["tripId"])
    private val buildReport = BuildReportData(trips, readings, excursions, custody, carriers, sensors, clock)
    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val publicKey = withContext(Dispatchers.IO) { signer.publicKey() }
            val report = try {
                buildReport(tripId, publicKey)
            } catch (_: IllegalStateException) {
                _state.value = TripDetailUiState(loading = false, missing = true)
                return@launch
            }
            val interval = report.trip.logIntervalSec
            val timeline = TripTimeline.build(report.readings, report.excursions, report.custody, report.gaps, interval)
            _state.value = TripDetailUiState(false, false, report, timeline, TripTimeline.alarms(timeline), TripTimeline.gapThresholdMs(interval))
        }
    }
}
