// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vassel.frostline.R
import com.vassel.frostline.data.nfc.NfcTagEvent
import com.vassel.frostline.data.nfc.TagInfo
import com.vassel.frostline.ui.components.SecondaryButton
import com.vassel.frostline.ui.components.SectionCard

private val SIM_TAGS = listOf(
    TagInfo("11111111-1111-4111-8111-111111111111", "Tag simulasi 1"),
    TagInfo("22222222-2222-4222-8222-222222222222", "Tag simulasi 2"),
    TagInfo("33333333-3333-4333-8333-333333333333", "Tag simulasi 3"),
)

/** Debug builds only: simulated tag taps so the whole handover flow runs without NFC hardware. */
@Composable
fun DebugHandoverTools(onTag: (NfcTagEvent) -> Unit) {
    SectionCard(stringResource(R.string.debug_tap_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SIM_TAGS.forEach { tag ->
                SecondaryButton(tag.label, { onTag(NfcTagEvent.Frostline(tag, "simulated")) })
            }
        }
    }
}

@Composable
fun DebugSettingsSection() {
    SectionCard(stringResource(R.string.debug_settings_title)) { Text(stringResource(R.string.debug_settings_text)) }
}
