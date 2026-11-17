// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.data.ble.BlePermissions
import com.vassel.frostline.data.ble.ScanProblem

@Composable
fun ScanScreen(onDone: () -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val missing = BlePermissions.forScan(Build.VERSION.SDK_INT)
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start() }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.start() }

    // Ask first, then scan. If the user declines, the scan reports the problem instead of failing silently.
    LaunchedEffect(Unit) {
        if (missing.isEmpty()) vm.start() else permissionLauncher.launch(missing.toTypedArray())
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodySmall)
        when (state.problem) {
            ScanProblem.PERMISSION -> {
                Text(stringResource(R.string.scan_problem_permission), color = MaterialTheme.colorScheme.error)
                SecondaryButton(stringResource(R.string.scan_grant), { permissionLauncher.launch(missing.toTypedArray()) })
            }
            ScanProblem.BLUETOOTH_OFF -> {
                Text(stringResource(R.string.scan_problem_bluetooth_off), color = MaterialTheme.colorScheme.error)
                SecondaryButton(stringResource(R.string.scan_enable_bluetooth), { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) })
            }
            ScanProblem.FAILED -> Text(stringResource(R.string.scan_problem_failed), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
        if (state.scanning) Text(stringResource(R.string.scan_scanning))
        else PrimaryButton(stringResource(R.string.scan_again), vm::start)
        if (state.sensors.isEmpty() && !state.scanning) Text(stringResource(R.string.scan_empty))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.sensors, key = { it.address }) { sensor ->
                Card(Modifier.fillMaxWidth().clickable { vm.pair(sensor, onDone) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(sensor.name ?: stringResource(R.string.scan_unnamed), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.scan_item_signal, sensor.rssi.toString(), sensor.macHash))
                    }
                }
            }
        }
    }
}
