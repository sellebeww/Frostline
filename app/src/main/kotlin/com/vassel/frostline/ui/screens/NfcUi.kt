// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vassel.frostline.R
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.data.nfc.NfcAvailability

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Explains why NFC cannot be used and, when it is only switched off, links to the NFC settings. */
@Composable
internal fun NfcUnavailable(availability: NfcAvailability) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (availability == NfcAvailability.NOT_SUPPORTED) R.string.nfc_not_supported else R.string.nfc_disabled),
            color = MaterialTheme.colorScheme.error,
        )
        if (availability == NfcAvailability.DISABLED) {
            SecondaryButton(stringResource(R.string.nfc_open_settings), { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) })
        }
    }
}
