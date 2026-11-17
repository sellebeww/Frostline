// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vassel.frostline.R
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.ui.theme.FrostlineTheme
import com.vassel.frostline.ui.theme.StatusPalette

/**
 * Temperature over time. Dashed lines mark the limits, excursions are shaded behind the line (with the type also given
 * in the description for screen readers), data gaps break the line. The line draws itself in when the chart appears.
 */
@Composable
fun TemperatureChart(
    readings: List<Reading>,
    minC: Double,
    maxC: Double,
    excursions: List<Excursion>,
    gapThresholdMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(readings.size) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val limitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val labelStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val sorted = remember(readings) { readings.sortedBy { it.tsUtcMs } }
    val description = if (sorted.isEmpty()) {
        stringResource(R.string.chart_empty)
    } else {
        stringResource(
            R.string.chart_description, sorted.size, Formats.temp(sorted.minOf { it.tempC }), Formats.temp(sorted.maxOf { it.tempC }), excursions.size,
        )
    }

    Canvas(modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
        if (sorted.isEmpty()) return@Canvas
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val plotW = size.width - left - 8.dp.toPx()
        val plotH = size.height - bottom - 8.dp.toPx()
        val t0 = sorted.first().tsUtcMs
        val t1 = maxOf(sorted.last().tsUtcMs, t0 + 1)
        val lo = minOf(minC, sorted.minOf { it.tempC }) - 1.0
        val hi = maxOf(maxC, sorted.maxOf { it.tempC }) + 1.0
        fun x(t: Long) = left + plotW * (t - t0).toFloat() / (t1 - t0).toFloat()
        fun y(c: Double) = 8.dp.toPx() + plotH * (1f - ((c - lo) / (hi - lo)).toFloat())

        // allowed band and limit lines
        drawRect(bandColor, Offset(left, y(maxC)), Size(plotW, y(minC) - y(maxC)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        for (limit in listOf(minC, maxC)) {
            drawLine(limitColor, Offset(left, y(limit)), Offset(left + plotW, y(limit)), strokeWidth = 2f, pathEffect = dash)
            val label = measurer.measure(Formats.temp(limit), labelStyle)
            drawText(label, topLeft = Offset(0f, y(limit) - label.size.height / 2f))
        }
        // excursions shaded behind the line
        for (e in excursions) {
            val colors = when (e.type) {
                ExcursionType.HEAT -> StatusPalette.Heat
                ExcursionType.COLD -> StatusPalette.Cold
                ExcursionType.FREEZE -> StatusPalette.Freeze
            }
            val from = x(e.startUtcMs.coerceIn(t0, t1))
            val to = x((e.endUtcMs ?: t1).coerceIn(t0, t1))
            drawRect(colors.container.copy(alpha = 0.85f), Offset(from, 8.dp.toPx()), Size(maxOf(to - from, 4f), plotH))
        }
        // the line, broken at gaps, drawn in from the left
        val path = Path()
        var previous: Reading? = null
        for (r in sorted) {
            val p = previous
            if (p == null || r.tsUtcMs - p.tsUtcMs > gapThresholdMs) path.moveTo(x(r.tsUtcMs), y(r.tempC)) else path.lineTo(x(r.tsUtcMs), y(r.tempC))
            previous = r
        }
        clipRect(left = left, right = left + plotW * progress.value + 1f) {
            drawPath(path, lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            if (sorted.size == 1) drawCircle(lineColor, 5.dp.toPx(), Offset(x(t0), y(sorted.first().tempC)))
        }
        // time axis
        val start = measurer.measure(Formats.time(t0), labelStyle)
        val end = measurer.measure(Formats.time(t1), labelStyle)
        drawText(start, topLeft = Offset(left, size.height - start.size.height))
        drawText(end, topLeft = Offset(left + plotW - end.size.width, size.height - end.size.height))
    }
}

@Preview(showBackground = true)
@Composable
private fun TemperatureChartPreview() = FrostlineTheme {
    val readings = (0..60).map { Reading(it * 60_000L, it * 60_000L, if (it in 20..35) 9.0 + (it % 3) * 0.2 else 5.0 + (it % 4) * 0.3) }
    TemperatureChart(readings, 2.0, 8.0, listOf(Excursion("t", ExcursionType.HEAT, 20 * 60_000L, 36 * 60_000L, 9.4)), 300_000L)
}
