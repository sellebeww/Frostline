// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.timeline.TimelineItem
import com.vassel.frostline.ui.components.Formats
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.SectionCard
import com.vassel.frostline.ui.components.TemperatureChart
import com.vassel.frostline.ui.components.formatDuration

@Composable
fun TripDetailScreen(vm: TripDetailViewModel = hiltViewModel()) {
    val reports = rememberReportActions()
    val state by vm.state.collectAsStateWithLifecycle()
    val report = state.report
    if (report == null) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (state.missing) R.string.detail_missing else R.string.db_loading))
        }
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(Formats.dateTime(report.trip.startedUtcMs), style = MaterialTheme.typography.titleMedium)
        }
        item {
            val s = report.stats
            SectionCard(stringResource(R.string.detail_stats)) {
                Text(stringResource(R.string.detail_stats_range, s.minC?.let(Formats::temp) ?: "-", s.maxC?.let(Formats::temp) ?: "-", s.meanC?.let(Formats::temp) ?: "-"))
                Text(stringResource(R.string.home_mkt, s.mktC?.let(Formats::temp) ?: "-"))
                Text(stringResource(R.string.home_out_of_range, formatDuration(s.outOfRangeMs)))
                Text(stringResource(R.string.detail_stats_counts, s.excursionCount, s.gapCount, formatDuration(s.gapTotalMs)))
                Text(
                    stringResource(
                        when {
                            report.custody.isEmpty() -> R.string.detail_chain_empty // an empty chain is vacuously valid, so do not say "valid"
                            report.chainVerification == ChainVerification.Valid -> R.string.detail_chain_valid
                            else -> R.string.detail_chain_invalid
                        },
                    ),
                    color = if (report.chainVerification == ChainVerification.Valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.detail_chart)) {
                TemperatureChart(report.readings, report.trip.thresholds.minC, report.trip.thresholds.maxC, report.excursions, state.gapThresholdMs)
            }
        }
        item {
            SectionCard(stringResource(R.string.detail_alarms)) {
                if (state.alarms.isEmpty()) Text(stringResource(R.string.detail_no_alarms))
                state.alarms.forEach { Text(rowText(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item { Text(stringResource(R.string.detail_timeline), style = MaterialTheme.typography.titleLarge) }
        items(state.timeline, key = { "${it::class.simpleName}-${it.tsUtcMs}-${it.hashCode()}" }) { item ->
            SectionCard(null, Modifier.animateItem()) { Text(rowText(item), style = MaterialTheme.typography.bodyLarge) }
        }
        item {
            val busy = reports.busyTrip.value != null
            SecondaryButton(
                stringResource(if (busy) R.string.report_working else R.string.detail_report),
                { reports.generate(report.trip.id) },
                enabled = report.trip.status == TripStatus.COMPLETED && !busy,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun rowText(item: TimelineItem): String = when (item) {
    is TimelineItem.ReadingsSummary ->
        stringResource(R.string.tl_readings, Formats.time(item.tsUtcMs), Formats.time(item.toUtcMs), item.count, Formats.temp(item.minC), Formats.temp(item.maxC))
    is TimelineItem.ExcursionStarted -> stringResource(
        R.string.tl_excursion_start, Formats.time(item.tsUtcMs), stringResource(typeText(item.excursion.type)), Formats.temp(item.excursion.peakC),
    )
    is TimelineItem.ExcursionEnded -> stringResource(R.string.tl_excursion_end, Formats.time(item.tsUtcMs), formatDuration(item.excursion.durationMs(item.tsUtcMs)))
    is TimelineItem.Custody -> {
        val e = item.event
        val lat = e.lat
        val lon = e.lon
        val place = if (lat != null && lon != null) stringResource(R.string.tl_place, Formats.decimal(lat, 5), Formats.decimal(lon, 5)) else stringResource(R.string.tl_no_place)
        stringResource(R.string.tl_custody, Formats.time(item.tsUtcMs), e.type.name, e.tagLabel ?: "-", place)
    }
    is TimelineItem.SensorLost -> stringResource(R.string.tl_sensor_lost, Formats.time(item.tsUtcMs))
    is TimelineItem.SensorRecovered -> stringResource(R.string.tl_sensor_recovered, Formats.time(item.tsUtcMs))
}

private fun typeText(t: ExcursionType) = when (t) {
    ExcursionType.HEAT -> R.string.type_heat
    ExcursionType.COLD -> R.string.type_cold
    ExcursionType.FREEZE -> R.string.type_freeze
}
