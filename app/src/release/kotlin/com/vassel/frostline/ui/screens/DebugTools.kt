// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.compose.runtime.Composable
import com.vassel.frostline.data.nfc.NfcTagEvent

/** Release builds have no simulator tools. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugHandoverTools(onTag: (NfcTagEvent) -> Unit) = Unit

@Composable
fun DebugSettingsSection() = Unit
