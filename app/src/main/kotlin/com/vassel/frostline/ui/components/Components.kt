// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vassel.frostline.R
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.ui.screens.HomeStatus
import com.vassel.frostline.ui.theme.FrostlineTheme
import com.vassel.frostline.ui.theme.StatusColors
import com.vassel.frostline.ui.theme.StatusPalette

/** Minimum touch target for field use: bigger than the 48 dp guideline. */
val MinTouch = 56.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = MinTouch), enabled = enabled, shape = RoundedCornerShape(16.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = MinTouch), enabled = enabled, shape = RoundedCornerShape(16.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

internal data class ChipSpec(val colors: StatusColors, val icon: ImageVector, val label: Int)

internal fun chipSpec(s: HomeStatus) = when (s) {
    HomeStatus.READY -> ChipSpec(StatusPalette.Neutral, Icons.Filled.Info, R.string.status_ready)
    HomeStatus.IDLE -> ChipSpec(StatusPalette.Neutral, Icons.Filled.Info, R.string.status_idle)
    HomeStatus.SAFE -> ChipSpec(StatusPalette.Safe, Icons.Filled.CheckCircle, R.string.status_safe)
    HomeStatus.HEAT -> ChipSpec(StatusPalette.Heat, Icons.Filled.KeyboardArrowUp, R.string.status_heat)
    HomeStatus.COLD -> ChipSpec(StatusPalette.Cold, Icons.Filled.KeyboardArrowDown, R.string.status_cold)
    HomeStatus.FREEZE -> ChipSpec(StatusPalette.Freeze, Icons.Filled.Warning, R.string.status_freeze)
    HomeStatus.SENSOR_LOST -> ChipSpec(StatusPalette.Lost, Icons.Filled.Clear, R.string.status_sensor_lost)
}

/** Status as icon plus text; the colour only reinforces it. Colours and the label animate when the status changes. */
@Composable
fun StatusChip(status: HomeStatus, modifier: Modifier = Modifier) {
    val spec = chipSpec(status)
    val container by animateColorAsState(spec.colors.container, tween(400), label = "chipContainer")
    val content by animateColorAsState(spec.colors.content, tween(400), label = "chipContent")
    Surface(modifier, color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        AnimatedContent(
            targetState = spec,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "chipContent",
        ) { s ->
            Row(
                Modifier.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(s.icon, contentDescription = null)
                Text(stringResource(s.label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The big readable temperature. The value slides when it changes. [stale] dims it (sensor silent). */
@Composable
fun BigTemperature(tempC: Double?, stale: Boolean, modifier: Modifier = Modifier) {
    val text = tempC?.let { Formats.temp(it) } ?: stringResource(R.string.temp_none)
    val description = tempC?.let { stringResource(R.string.temp_description, Formats.temp(it)) } ?: stringResource(R.string.temp_none_description)
    AnimatedContent(
        targetState = text,
        transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 3 }) },
        modifier = modifier.semantics { contentDescription = description },
        label = "temperature",
    ) { value ->
        Row(verticalAlignment = Alignment.Top) {
            Text(
                value,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (stale) 0.55f else 1f),
            )
            Text(stringResource(R.string.unit_celsius), fontSize = 28.sp, modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Stays until the excursion is closed. The icon pulses gently; the text carries the meaning. */
@Composable
fun AlarmBanner(openExcursion: ExcursionType?, sensorLost: Boolean, modifier: Modifier = Modifier) {
    val visible = openExcursion != null || sensorLost
    AnimatedVisibility(visible, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.55f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha",
        )
        val colors = when {
            openExcursion == ExcursionType.FREEZE -> StatusPalette.Freeze
            openExcursion == ExcursionType.HEAT -> StatusPalette.Heat
            openExcursion == ExcursionType.COLD -> StatusPalette.Cold
            else -> StatusPalette.Lost
        }
        Surface(Modifier.fillMaxWidth(), color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (openExcursion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.alpha(pulse.value))
                        Text(stringResource(bannerTitle(openExcursion)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.banner_excursion_hint), style = MaterialTheme.typography.bodyMedium)
                }
                if (sensorLost) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.alpha(pulse.value))
                        Text(stringResource(R.string.banner_sensor_lost), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun bannerTitle(type: ExcursionType) = when (type) {
    ExcursionType.HEAT -> R.string.banner_heat
    ExcursionType.COLD -> R.string.banner_cold
    ExcursionType.FREEZE -> R.string.banner_freeze
}

@Preview(showBackground = true)
@Composable
private fun StatusChipsPreview() = FrostlineTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeStatus.entries.forEach { StatusChip(it) }
    }
}

@Preview(showBackground = true)
@Composable
private fun BigTemperaturePreview() = FrostlineTheme {
    Column(Modifier.padding(16.dp)) {
        BigTemperature(5.2, stale = false)
        AlarmBanner(ExcursionType.HEAT, sensorLost = true)
    }
}
