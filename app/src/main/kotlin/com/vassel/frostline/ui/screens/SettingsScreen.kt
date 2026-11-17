// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.SectionCard
import com.vassel.frostline.ui.theme.FrostlineTheme

@Composable
fun SettingsScreen(onBatteryGuide: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        ThresholdSection(state, vm::onMin, vm::onMax, vm::onFreeze, vm::save, vm::resetDefaults)

        SectionCard(stringResource(R.string.settings_interval_title)) {
            Text(stringResource(R.string.settings_interval_value, state.intervalSec), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = state.intervalSec.toFloat(),
                onValueChange = { vm.setInterval(it.toInt() / 10 * 10) },
                valueRange = AppSettings.MIN_INTERVAL_SEC.toFloat()..AppSettings.MAX_INTERVAL_SEC.toFloat(),
            )
            Text(stringResource(R.string.settings_interval_hint), style = MaterialTheme.typography.bodySmall)
        }
        SecondaryButton(stringResource(R.string.home_battery), onBatteryGuide)
        SectionCard(stringResource(R.string.about_title)) {
            Text(stringResource(R.string.about_version, version))
            Text(stringResource(R.string.about_owner))
            Text(stringResource(R.string.about_license))
            Text(stringResource(R.string.home_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
        DebugSettingsSection()
    }
}

/** The threshold editor. State is hoisted: it only draws what it is given. */
@Composable
fun ThresholdSection(
    state: SettingsUiState,
    onMin: (String) -> Unit,
    onMax: (String) -> Unit,
    onFreeze: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    SectionCard(stringResource(R.string.settings_threshold_title)) {
        Text(stringResource(R.string.settings_threshold_note), style = MaterialTheme.typography.bodyMedium)
        ThresholdField(R.string.settings_min, state.minText, onMin, ThresholdError.MIN_INVALID in state.errors || ThresholdError.MIN_NOT_BELOW_MAX in state.errors)
        ThresholdField(R.string.settings_max, state.maxText, onMax, ThresholdError.MAX_INVALID in state.errors || ThresholdError.MIN_NOT_BELOW_MAX in state.errors)
        ThresholdField(R.string.settings_freeze, state.freezeText, onFreeze, ThresholdError.FREEZE_INVALID in state.errors || ThresholdError.FREEZE_NOT_BELOW_MIN in state.errors)
        for (error in state.errors) Text(stringResource(errorText(error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        AnimatedVisibility(state.saved) { Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
        PrimaryButton(stringResource(R.string.settings_save), onSave, enabled = state.errors.isEmpty())
        SecondaryButton(stringResource(R.string.settings_reset), onReset)
    }
}

@Composable
private fun ThresholdField(label: Int, value: String, onChange: (String) -> Unit, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        suffix = { Text(stringResource(R.string.unit_celsius)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.padding(0.dp),
    )
}

private fun errorText(e: ThresholdError) = when (e) {
    ThresholdError.MIN_INVALID, ThresholdError.MAX_INVALID, ThresholdError.FREEZE_INVALID -> R.string.settings_error_number
    ThresholdError.MIN_NOT_BELOW_MAX -> R.string.settings_error_min_max
    ThresholdError.FREEZE_NOT_BELOW_MIN -> R.string.settings_error_freeze_min
}

@Preview(showBackground = true)
@Composable
private fun ThresholdSectionPreview() = FrostlineTheme {
    Column(Modifier.padding(16.dp)) {
        ThresholdSection(SettingsUiState("2,0", "1,0", "3,0", setOf(ThresholdError.MIN_NOT_BELOW_MAX, ThresholdError.FREEZE_NOT_BELOW_MIN)), {}, {}, {}, {}, {})
    }
}
