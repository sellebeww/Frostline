// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vassel.frostline.R
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.PrimaryButton
import com.vassel.frostline.data.nfc.NdefCodec
import com.vassel.frostline.data.nfc.NfcAvailability
import com.vassel.frostline.data.nfc.WriteResult

@Composable
fun WriteTagScreen(vm: WriteTagViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val availability = vm.availability()
    DisposableEffect(Unit) { onDispose { vm.cancel() } }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.write_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.write_intro))
        if (availability != NfcAvailability.AVAILABLE) {
            NfcUnavailable(availability)
            return@Column
        }
        OutlinedTextField(
            value = state.label,
            onValueChange = vm::setLabel,
            label = { Text(stringResource(R.string.write_label)) },
            supportingText = { Text(stringResource(R.string.write_label_hint)) },
            singleLine = true,
            enabled = !state.waiting,
        )
        val problem = when {
            state.labelProblem == NdefCodec.LabelProblem.EMPTY -> R.string.write_label_empty
            state.labelProblem == NdefCodec.LabelProblem.TOO_LONG -> R.string.write_label_too_long
            state.labelProblem == NdefCodec.LabelProblem.CONTROL_CHARACTER -> R.string.write_label_control
            state.uriTooLong -> R.string.write_uri_too_long
            else -> null
        }
        problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (state.waiting) {
            Text(stringResource(R.string.write_waiting))
            SecondaryButton(stringResource(R.string.write_cancel), vm::cancel)
        } else {
            PrimaryButton(stringResource(R.string.write_start), { activity?.let(vm::start) })
        }
        state.result?.let { r ->
            if (r == WriteResult.SUCCESS) {
                Text(stringResource(R.string.write_ok, state.writtenLabel ?: ""))
            } else {
                Text(stringResource(resultText(r)), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun resultText(r: WriteResult) = when (r) {
    WriteResult.READ_ONLY -> R.string.write_read_only
    WriteResult.TOO_SMALL -> R.string.write_too_small
    WriteResult.UNSUPPORTED -> R.string.write_unsupported
    WriteResult.VERIFY_FAILED -> R.string.write_verify_failed
    WriteResult.IO_ERROR -> R.string.write_io_error
    WriteResult.NFC_UNAVAILABLE -> R.string.nfc_disabled
    WriteResult.SUCCESS -> R.string.write_ok
}
