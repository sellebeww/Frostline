// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E99),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E),
    background = Color(0xFFF5FAFD),
    surface = Color(0xFFF5FAFD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DCFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFB6C9D8),
    background = Color(0xFF0F1417),
    surface = Color(0xFF0F1417),
)

@Composable
fun FrostlineTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
