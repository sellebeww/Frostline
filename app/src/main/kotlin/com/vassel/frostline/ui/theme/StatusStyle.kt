// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.theme

import androidx.compose.ui.graphics.Color

/** A chip palette. Text and background are chosen together and checked for WCAG AA contrast in tests. */
data class StatusColors(val container: Color, val content: Color)

object StatusPalette {
    val Safe = StatusColors(Color(0xFFD3F0DF), Color(0xFF0A3B22))
    val Heat = StatusColors(Color(0xFFFFD9CF), Color(0xFF5A1300))
    val Cold = StatusColors(Color(0xFFD2E6FF), Color(0xFF002F57))
    val Freeze = StatusColors(Color(0xFFE5D9FF), Color(0xFF2A0070))
    val Lost = StatusColors(Color(0xFFFFE7A3), Color(0xFF3B2C00))
    val Neutral = StatusColors(Color(0xFFE1E8EE), Color(0xFF1B2A35))
}
