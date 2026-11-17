// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.OperatorStore
import com.vassel.frostline.data.nfc.ForeignReason
import com.vassel.frostline.data.nfc.NfcAvailability
import com.vassel.frostline.data.nfc.NfcReader
import com.vassel.frostline.data.nfc.NfcTagEvent
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.port.TransactionRunner
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.TapGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HandoverUiState(
    val candidate: HandoverCandidate? = null,
    val deliver: Boolean = false,
    val foreign: ForeignReason? = null,
    val noTrip: Boolean = false,
    val noOperator: Boolean = false,
    val rejected: RejectReason? = null,
    val recorded: CustodyEvent? = null,
    val tripCompleted: Boolean = false,
)

@HiltViewModel
class HandoverViewModel @Inject constructor(
    private val monitor: com.vassel.frostline.service.MonitorController,
    private val reader: NfcReader,
    private val operators: OperatorStore,
    trips: TripRepository,
    readings: ReadingRepository,
    custody: CustodyRepository,
    excursions: ExcursionRepository,
    location: LocationProvider,
    signer: Signer,
    transactions: TransactionRunner,
    clock: Clock,
) : ViewModel() {
    private val controller: HandoverController = run {
        val record = RecordCustodyEvent(trips, readings, custody, excursions, location, signer, transactions, clock)
        HandoverController(
            trips, readings, custody, record, CompleteTrip(record, trips, excursions, transactions), location, TapGate(clock),
            onTripCompleted = { monitor.stop() },
        )
    }

    private val _state = MutableStateFlow(HandoverUiState())
    val state: StateFlow<HandoverUiState> = _state.asStateFlow()
    private var locating: Job? = null

    fun availability(): NfcAvailability = reader.availability()

    fun tags(activity: Activity): Flow<NfcTagEvent> = reader.tags(activity)

    fun onNfcEvent(event: NfcTagEvent) {
        when (event) {
            is NfcTagEvent.Foreign -> _state.update { HandoverUiState(foreign = event.reason) }
            is NfcTagEvent.Frostline -> viewModelScope.launch {
                when (val outcome = controller.onTag(event.info, event.uidHash)) {
                    TapOutcome.Ignored -> Unit
                    TapOutcome.NoActiveTrip -> _state.update { HandoverUiState(noTrip = true) }
                    is TapOutcome.Candidate -> {
                        locating?.cancel()
                        _state.value = HandoverUiState(candidate = outcome.candidate)
                        locating = launch {
                            val located = controller.locate(outcome.candidate)
                            _state.update { if (it.candidate?.tag == located.tag) it.copy(candidate = located) else it }
                        }
                    }
                }
            }
        }
    }

    fun setDeliver(value: Boolean) = _state.update { it.copy(deliver = value) }

    fun cancel() {
        locating?.cancel()
        _state.value = HandoverUiState()
    }

    fun confirm() {
        val current = _state.value
        val candidate = current.candidate ?: return
        val operator = operators.get()
        if (operator == null) {
            _state.update { it.copy(noOperator = true) }
            return
        }
        // Clear the candidate first: a second quick press then finds nothing to record (no duplicate event).
        _state.update { it.copy(candidate = null) }
        viewModelScope.launch {
            when (val result = controller.confirm(candidate, current.deliver, operator.userId)) {
                is ConfirmOutcome.Recorded -> _state.value = HandoverUiState(recorded = result.event, tripCompleted = result.tripCompleted)
                is ConfirmOutcome.Rejected -> _state.value = HandoverUiState(rejected = result.reason)
            }
        }
    }
}
