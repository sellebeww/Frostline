// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vassel.frostline.R
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.MinTouch
import androidx.compose.foundation.layout.heightIn

/** Explains the battery setting and opens the system screen. It never requests the exemption by itself. */
@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.battery_intro))
        Text(stringResource(R.string.battery_steps))
        Text(stringResource(R.string.battery_oem), style = MaterialTheme.typography.bodySmall)
        PrimaryButton(stringResource(R.string.battery_open_settings), { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.back)) }
    }
}
