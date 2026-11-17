// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.State
import com.vassel.frostline.R
import com.vassel.frostline.data.report.ReportSharer

/** Handle returned by [rememberReportActions]: [busyTrip] is the trip being processed, [generate] starts one. */
class ReportActions(val busyTrip: State<String?>, val generate: (String) -> Unit)

/** Wires a [ReportViewModel] to the share sheet and to short messages for the two failure cases. */
@Composable
fun rememberReportActions(vm: ReportViewModel = hiltViewModel()): ReportActions {
    val context = LocalContext.current
    val title = stringResource(R.string.report_share_title)
    val notCompleted = stringResource(R.string.report_not_completed)
    val failed = stringResource(R.string.report_failed)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ReportEvent.Share -> context.startActivity(ReportSharer.shareIntent(context, event.files, title))
                ReportEvent.NotCompleted -> Toast.makeText(context, notCompleted, Toast.LENGTH_LONG).show()
                ReportEvent.Failed -> Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    val busy = vm.workingTrip.collectAsStateWithLifecycle()
    return ReportActions(busy, vm::generate)
}
