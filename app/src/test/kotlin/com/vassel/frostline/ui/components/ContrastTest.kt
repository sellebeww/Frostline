// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.components

import androidx.compose.ui.graphics.Color
import com.vassel.frostline.ui.theme.StatusColors
import com.vassel.frostline.ui.theme.StatusPalette
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {
    private fun lin(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun everyStatusChipMeetsWcagAaForNormalText() {
        mapOf(
            "safe" to StatusPalette.Safe, "heat" to StatusPalette.Heat, "cold" to StatusPalette.Cold,
            "freeze" to StatusPalette.Freeze, "lost" to StatusPalette.Lost, "neutral" to StatusPalette.Neutral,
        ).forEach { (name, c: StatusColors) -> assertTrue(ratio(c.container, c.content) >= 4.5, "$name ${ratio(c.container, c.content)}") }
    }

    @Test
    fun theContrastFunctionIsRight() {
        assertTrue(ratio(Color.Black, Color.White) > 20.9)
        assertTrue(ratio(Color.White, Color.White) < 1.01)
    }
}
