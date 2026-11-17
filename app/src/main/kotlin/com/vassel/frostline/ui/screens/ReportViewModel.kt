// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.report.GeneratedReport
import com.vassel.frostline.data.report.ReportGenerator
import com.vassel.frostline.data.report.TripNotCompletedException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface ReportEvent {
    data class Share(val files: List<File>) : ReportEvent
    data object NotCompleted : ReportEvent
    data object Failed : ReportEvent
}

/** Generates the report of one trip at a time and tells the screen when there is something to share. */
@HiltViewModel
class ReportViewModel @Inject constructor(private val generator: ReportGenerator) : ViewModel() {
    private val _workingTrip = MutableStateFlow<String?>(null)

    /** The id of the trip being processed, or null. */
    val workingTrip: StateFlow<String?> = _workingTrip.asStateFlow()

    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events: Flow<ReportEvent> = _events.receiveAsFlow()

    fun generate(tripId: String) {
        if (_workingTrip.value != null) return
        _workingTrip.value = tripId
        viewModelScope.launch {
            val event = try {
                val r: GeneratedReport = generator.generate(tripId)
                ReportEvent.Share(r.files)
            } catch (_: TripNotCompletedException) {
                ReportEvent.NotCompleted
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReportEvent.Failed
            } finally {
                _workingTrip.value = null
            }
            _events.send(event)
        }
    }
}
