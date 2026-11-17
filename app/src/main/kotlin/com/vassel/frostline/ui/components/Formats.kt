// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vassel.frostline.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ID = Locale.forLanguageTag("id-ID")

/** Display formats (Indonesian: decimal comma). Storage and hashes never use these. */
object Formats {
    fun temp(value: Double): String = String.format(ID, "%.1f", value)
    fun decimal(value: Double, places: Int): String = String.format(ID, "%.${places}f", value)
    fun dateTime(utcMs: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", ID).format(Date(utcMs))
    fun time(utcMs: Long): String = SimpleDateFormat("HH:mm", ID).format(Date(utcMs))
}

@Composable
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) stringResource(R.string.duration_hm, minutes / 60, minutes % 60) else stringResource(R.string.duration_m, minutes)
}
