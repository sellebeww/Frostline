// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.data.ble.BlePermissions
import com.vassel.frostline.ui.components.Formats
import com.vassel.frostline.ui.components.MinTouch
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.SectionCard

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StartTripScreen(onPickSensor: () -> Unit, onStarted: () -> Unit, onSettings: () -> Unit, vm: StartTripViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.events.collect { onStarted() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start() }
    val onStart = {
        val missing = BlePermissions.forMonitoring(Build.VERSION.SDK_INT)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.start() else launcher.launch(missing.toTypedArray())
    }

    // The start button stays pinned at the bottom (reachable with one thumb); the form scrolls above it.
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.start_trip_title), style = MaterialTheme.typography.headlineMedium)

        SectionCard(stringResource(R.string.start_carrier_title)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.carriers.forEach { c ->
                    FilterChip(
                        selected = state.selectedCarrierId == c.id,
                        onClick = { vm.selectCarrier(if (state.selectedCarrierId == c.id) null else c.id) },
                        label = { Text(c.name) },
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }
            OutlinedTextField(
                value = state.newCarrierName,
                onValueChange = vm::onNewCarrierName,
                label = { Text(stringResource(R.string.start_carrier_new)) },
                supportingText = { Text(stringResource(R.string.start_carrier_hint)) },
                singleLine = true,
            )
        }

        SectionCard(stringResource(R.string.start_threshold_title)) {
            Text(stringResource(R.string.start_threshold_values, Formats.temp(state.thresholds.minC), Formats.temp(state.thresholds.maxC), Formats.temp(state.thresholds.freezeC)))
            SecondaryButton(stringResource(R.string.start_threshold_edit), onSettings)
        }

        SectionCard(stringResource(R.string.home_sensor_title)) {
            val sensor = state.sensor
            Text(if (sensor == null) stringResource(R.string.home_no_sensor) else stringResource(R.string.home_sensor_paired, sensor.model ?: stringResource(R.string.scan_unnamed), sensor.macHash))
            SecondaryButton(stringResource(if (sensor == null) R.string.home_pick_sensor else R.string.home_change_sensor), onPickSensor)
        }

        SectionCard(stringResource(R.string.settings_interval_title)) {
            Text(stringResource(R.string.settings_interval_value, state.intervalSec), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = state.intervalSec.toFloat(),
                onValueChange = { vm.setInterval(it.toInt() / 10 * 10) },
                valueRange = AppSettings.MIN_INTERVAL_SEC.toFloat()..AppSettings.MAX_INTERVAL_SEC.toFloat(),
            )
        }

    }
        AnimatedVisibility(state.error != null) {
            Text(stringResource(errorText(state.error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        }
        PrimaryButton(stringResource(R.string.start_trip_go), onStart, enabled = !state.busy)
    }
}

private fun errorText(e: StartTripError?) = when (e) {
    StartTripError.NO_SENSOR -> R.string.home_need_sensor
    StartTripError.NO_OPERATOR -> R.string.home_need_operator
    StartTripError.TRIP_ACTIVE -> R.string.start_error_active
    StartTripError.INTERVAL -> R.string.start_error_interval
    StartTripError.CARRIER_NAME_TOO_LONG, null -> R.string.start_error_active
}
