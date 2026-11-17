// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.nfc.NdefCodec
import com.vassel.frostline.data.nfc.NfcAvailability
import com.vassel.frostline.data.nfc.NfcReader
import com.vassel.frostline.data.nfc.NfcWriter
import com.vassel.frostline.data.nfc.WriteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WriteUiState(
    val label: String = "",
    val labelProblem: NdefCodec.LabelProblem? = null,
    val uriTooLong: Boolean = false,
    val waiting: Boolean = false,
    val result: WriteResult? = null,
    val writtenLabel: String? = null,
)

@HiltViewModel
class WriteTagViewModel @Inject constructor(
    private val writer: NfcWriter,
    private val reader: NfcReader,
) : ViewModel() {
    private val _state = MutableStateFlow(WriteUiState())
    val state: StateFlow<WriteUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun availability(): NfcAvailability = reader.availability()

    fun setLabel(value: String) = _state.update { it.copy(label = value, labelProblem = null, uriTooLong = false, result = null) }

    fun start(activity: Activity) {
        val label = _state.value.label
        val problem = NdefCodec.validateLabel(label)
        if (problem != null) {
            _state.update { it.copy(labelProblem = problem) }
            return
        }
        val uri = try {
            NdefCodec.encode(UUID.randomUUID(), label)
        } catch (_: IllegalArgumentException) {
            _state.update { it.copy(uriTooLong = true) }
            return
        }
        job?.cancel()
        _state.update { it.copy(waiting = true, result = null) }
        job = viewModelScope.launch {
            val result = writer.write(activity, uri)
            _state.update { it.copy(waiting = false, result = result, writtenLabel = if (result == WriteResult.SUCCESS) label.trim() else null) }
        }
    }

    /** Also called when the screen is left, so reader mode never outlives it. */
    fun cancel() {
        job?.cancel()
        _state.update { it.copy(waiting = false) }
    }
}
