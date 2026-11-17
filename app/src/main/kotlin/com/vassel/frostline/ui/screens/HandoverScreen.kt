// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.vassel.frostline.ui.components.MinTouch
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.data.nfc.ForeignReason
import com.vassel.frostline.data.nfc.NfcAvailability
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationResult
import kotlinx.coroutines.launch

@Composable
fun HandoverScreen(vm: HandoverViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val availability = vm.availability()

    // Location is asked for the first time it is needed. Declining never blocks recording.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val missing = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.size == 2) permissionLauncher.launch(missing.toTypedArray())
    }

    // Reader mode only while this screen is resumed.
    LifecycleResumeEffect(availability, activity) {
        val job = if (availability == NfcAvailability.AVAILABLE && activity != null) {
            scope.launch { vm.tags(activity).collect(vm::onNfcEvent) }
        } else {
            null
        }
        onPauseOrDispose { job?.cancel() }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.handover_title), style = MaterialTheme.typography.headlineMedium)
        DebugHandoverTools(vm::onNfcEvent)
        // Without NFC no tag can arrive in release, but debug simulated taps still drive the flow below.
        if (availability != NfcAvailability.AVAILABLE) NfcUnavailable(availability)
        val candidate = state.candidate
        val recorded = state.recorded
        when {
            recorded != null -> {
                Text(stringResource(R.string.handover_recorded, recorded.type.name, recorded.seq.toString()))
                if (state.tripCompleted) Text(stringResource(R.string.handover_trip_done))
            }
            candidate != null -> CandidateCard(candidate, state.deliver, vm::setDeliver, vm::confirm, vm::cancel)
            availability == NfcAvailability.AVAILABLE -> Text(stringResource(R.string.handover_waiting))
        }
        state.foreign?.let { Text(stringResource(foreignText(it)), color = MaterialTheme.colorScheme.error) }
        if (state.noTrip) Text(stringResource(R.string.handover_no_trip), color = MaterialTheme.colorScheme.error)
        if (state.noOperator) Text(stringResource(R.string.home_need_operator), color = MaterialTheme.colorScheme.error)
        state.rejected?.let {
            Text(
                stringResource(if (it == RejectReason.INVALID_SEQUENCE) R.string.handover_reject_sequence else R.string.handover_reject_trip),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CandidateCard(
    c: HandoverCandidate,
    deliver: Boolean,
    onDeliver: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.handover_tag, c.tag.label), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.handover_tag_id, c.tag.tagId.take(8)), style = MaterialTheme.typography.bodySmall)
        val type = if (deliver) CustodyType.DELIVERY else c.proposed
        Text(stringResource(R.string.handover_event_type, stringResource(typeText(type))))
        if (c.canDeliver) {
            // The whole row toggles, so the label is as easy to hit as the box.
            Row(
                Modifier.heightIn(min = MinTouch).toggleable(value = deliver, role = Role.Checkbox, onValueChange = onDeliver),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = deliver, onCheckedChange = null)
                Text(stringResource(R.string.handover_is_final), modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(c.tempC?.let { stringResource(R.string.handover_temp, com.vassel.frostline.notification.AlarmNotifier.formatTemp(it)) } ?: stringResource(R.string.handover_temp_none))
        Text(locationText(c.location))
        if (c.sameTagAsLast) Text(stringResource(R.string.handover_same_tag_warning), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(if (c.sameTagAsLast) R.string.handover_confirm_same_tag else R.string.handover_confirm), onConfirm, Modifier.weight(1f), enabled = c.location != null)
            SecondaryButton(stringResource(R.string.handover_cancel), onCancel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun locationText(r: LocationResult?): String = when (r) {
    null -> stringResource(R.string.handover_locating)
    is LocationResult.Fix -> {
        val lat = String.format(java.util.Locale.forLanguageTag("id-ID"), "%.5f", r.location.lat)
        val lon = String.format(java.util.Locale.forLanguageTag("id-ID"), "%.5f", r.location.lon)
        if (r.approximate) stringResource(R.string.handover_location_approx, lat, lon)
        else stringResource(R.string.handover_location_ok, lat, lon, r.location.accuracyM.toInt().toString())
    }
    is LocationResult.None -> stringResource(
        when (r.reason) {
            LocationFailure.PERMISSION_DENIED -> R.string.handover_location_none_permission
            LocationFailure.TIMEOUT -> R.string.handover_location_none_timeout
            LocationFailure.UNAVAILABLE -> R.string.handover_location_none_unavailable
        },
    )
}

private fun typeText(t: CustodyType) = when (t) {
    CustodyType.PICKUP -> R.string.handover_type_pickup
    CustodyType.HANDOFF -> R.string.handover_type_handoff
    CustodyType.DELIVERY -> R.string.handover_type_delivery
}

private fun foreignText(r: ForeignReason) = when (r) {
    ForeignReason.NOT_FROSTLINE -> R.string.handover_foreign_not_frostline
    ForeignReason.EMPTY -> R.string.handover_foreign_empty
    ForeignReason.MALFORMED -> R.string.handover_foreign_malformed
    ForeignReason.TOO_LONG -> R.string.handover_foreign_too_long
    ForeignReason.UNREADABLE -> R.string.handover_foreign_unreadable
}
