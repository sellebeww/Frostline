// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import androidx.compose.foundation.layout.heightIn
import com.vassel.frostline.ui.components.MinTouch
import com.vassel.frostline.data.ble.BlePermissions
import com.vassel.frostline.domain.port.SensorFailure
import com.vassel.frostline.ui.components.AlarmBanner
import com.vassel.frostline.ui.components.BigTemperature
import com.vassel.frostline.ui.components.Formats
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.SectionCard
import com.vassel.frostline.ui.components.StatusChip
import com.vassel.frostline.ui.components.formatDuration

@Composable
fun HomeScreen(
    onStartTrip: () -> Unit,
    onPickSensor: () -> Unit,
    onBatteryGuide: () -> Unit,
    onHandover: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val blockedText = stringResource(R.string.home_start_blocked)
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { if (it == HomeEvent.StartBlocked) snackbar.showSnackbar(blockedText) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) vm.resume()
    }
    val onResume = {
        val missing = BlePermissions.forMonitoring(Build.VERSION.SDK_INT)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.resume() else launcher.launch(missing.toTypedArray())
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusChip(state.status)
            BigTemperature(state.tempC, stale = state.sensorLost || !state.running)
            AlarmBanner(state.openExcursion, state.sensorLost)
            state.failure?.let { Text(stringResource(failureText(it)), color = MaterialTheme.colorScheme.error) }

            AnimatedVisibility(state.hasActiveTrip) {
                SectionCard(stringResource(R.string.home_monitoring_title)) {
                    Text(state.mktC?.let { stringResource(R.string.home_mkt, Formats.temp(it)) } ?: stringResource(R.string.home_mkt_none), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_out_of_range, formatDuration(state.outOfRangeMs)), style = MaterialTheme.typography.titleMedium)
                }
            }

            SectionCard(stringResource(R.string.home_sensor_title)) {
                val sensor = state.sensor
                Text(
                    if (sensor == null) stringResource(R.string.home_no_sensor)
                    else stringResource(R.string.home_sensor_paired, sensor.model ?: stringResource(R.string.scan_unnamed), sensor.macHash),
                )
                SecondaryButton(stringResource(if (sensor == null) R.string.home_pick_sensor else R.string.home_change_sensor), onPickSensor)
            }

            when {
                !state.hasActiveTrip -> PrimaryButton(stringResource(R.string.home_start_trip), onStartTrip)
                state.running -> SecondaryButton(stringResource(R.string.home_stop), vm::stop)
                else -> PrimaryButton(stringResource(R.string.home_resume), onResume)
            }
            if (state.hasActiveTrip) PrimaryButton(stringResource(R.string.home_handover), onHandover)
            TextButton(onClick = onBatteryGuide, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.home_battery)) }
            Text(stringResource(R.string.home_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun failureText(f: SensorFailure) = when (f) {
    SensorFailure.NO_ESS_SERVICE -> R.string.failure_no_ess
    SensorFailure.NO_TEMPERATURE_CHARACTERISTIC -> R.string.failure_no_temp
    SensorFailure.BLUETOOTH_OFF -> R.string.failure_bluetooth_off
    SensorFailure.PERMISSION_DENIED -> R.string.failure_permission
}
