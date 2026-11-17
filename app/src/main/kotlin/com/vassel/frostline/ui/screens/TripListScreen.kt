// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.ui.components.Formats
import com.vassel.frostline.ui.components.formatDuration

@Composable
fun TripListScreen(onOpen: (String) -> Unit, vm: TripListViewModel = hiltViewModel()) {
    val reports = rememberReportActions()
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_trips), style = MaterialTheme.typography.headlineMedium)
        if (state.items.isEmpty() && !state.loading) Text(stringResource(R.string.trips_empty))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items, key = { it.trip.id }) { item ->
                Card(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(item.trip.id) }.animateItem()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Formats.dateTime(item.trip.startedUtcMs), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (item.trip.status == TripStatus.ACTIVE) R.string.trip_status_active else R.string.trip_status_done) +
                                (item.carrierName?.let { " · $it" } ?: ""),
                        )
                        Text(
                            stringResource(
                                R.string.trip_summary,
                                item.stats.mktC?.let(Formats::temp) ?: stringResource(R.string.temp_none),
                                item.stats.excursionCount,
                                formatDuration(item.stats.outOfRangeMs),
                            ),
                        )
                        if (item.trip.status == TripStatus.COMPLETED) {
                            val busy = reports.busyTrip.value != null
                            TextButton(onClick = { reports.generate(item.trip.id) }, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp)) {
                                Text(stringResource(if (reports.busyTrip.value == item.trip.id) R.string.report_working else R.string.report_action))
                            }
                        }
                    }
                }
            }
        }
    }
}
