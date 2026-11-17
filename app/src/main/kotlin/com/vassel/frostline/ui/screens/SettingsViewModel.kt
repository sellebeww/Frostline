// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.port.ThresholdRepository
import com.vassel.frostline.ui.components.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val minText: String = "",
    val maxText: String = "",
    val freezeText: String = "",
    val errors: Set<ThresholdError> = emptySet(),
    val intervalSec: Int = AppSettings.DEFAULT_INTERVAL_SEC,
    /** True right after a successful save, so the screen can confirm it. */
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val thresholds: ThresholdRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(intervalSec = settings.logIntervalSec))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { show(thresholds.getDefault()) }
    }

    private fun show(p: ThresholdProfile) = _state.update {
        it.copy(minText = Formats.temp(p.minC), maxText = Formats.temp(p.maxC), freezeText = Formats.temp(p.freezeC), errors = emptySet(), loaded = true)
    }

    fun onMin(text: String) = edit { it.copy(minText = text) }
    fun onMax(text: String) = edit { it.copy(maxText = text) }
    fun onFreeze(text: String) = edit { it.copy(freezeText = text) }

    private fun edit(change: (SettingsUiState) -> SettingsUiState) = _state.update {
        val next = change(it).copy(saved = false)
        next.copy(errors = (ThresholdForm.validate(next.minText, next.maxText, next.freezeText) as? ThresholdResult.Invalid)?.errors.orEmpty())
    }

    fun save() {
        val s = _state.value
        when (val r = ThresholdForm.validate(s.minText, s.maxText, s.freezeText)) {
            is ThresholdResult.Invalid -> _state.update { it.copy(errors = r.errors, saved = false) }
            is ThresholdResult.Valid -> viewModelScope.launch {
                thresholds.saveDefault(r.profile)
                _state.update { it.copy(errors = emptySet(), saved = true) }
            }
        }
    }

    fun resetDefaults() {
        show(ThresholdProfile())
        _state.update { it.copy(saved = false) }
    }

    fun setInterval(seconds: Int) {
        settings.logIntervalSec = seconds
        _state.update { it.copy(intervalSec = settings.logIntervalSec) }
    }
}
