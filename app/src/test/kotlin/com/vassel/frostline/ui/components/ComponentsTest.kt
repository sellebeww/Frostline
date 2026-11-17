// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vassel.frostline.R
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.ui.screens.HomeStatus
import com.vassel.frostline.ui.screens.SettingsUiState
import com.vassel.frostline.ui.screens.ThresholdError
import com.vassel.frostline.ui.screens.ThresholdSection
import com.vassel.frostline.ui.theme.FrostlineTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun everyStatusChipShowsItsOwnTextNotJustAColour() {
        val expected = mapOf(
            HomeStatus.SAFE to R.string.status_safe, HomeStatus.HEAT to R.string.status_heat, HomeStatus.COLD to R.string.status_cold,
            HomeStatus.FREEZE to R.string.status_freeze, HomeStatus.SENSOR_LOST to R.string.status_sensor_lost,
            HomeStatus.READY to R.string.status_ready, HomeStatus.IDLE to R.string.status_idle,
        )
        var status by androidx.compose.runtime.mutableStateOf(HomeStatus.SAFE)
        compose.setContent { FrostlineTheme { StatusChip(status) } }
        for ((target, label) in expected) {
            status = target
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(text(label)).assertExists()
        }
    }

    @Test
    fun theAlarmBannerStaysWhileAnExcursionIsOpenAndLeavesWhenItCloses() {
        var open by androidx.compose.runtime.mutableStateOf<ExcursionType?>(ExcursionType.HEAT)
        compose.setContent { FrostlineTheme { AlarmBanner(open, sensorLost = false) } }
        compose.onNodeWithText(text(R.string.banner_heat)).assertExists()
        open = null
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(text(R.string.banner_heat)).assertDoesNotExist()
    }

    @Test
    fun theBigTemperatureUsesTheDecimalComma() {
        compose.setContent { FrostlineTheme { BigTemperature(5.2, stale = false) } }
        compose.onNodeWithText("5,2").assertExists()
    }

    @Test
    fun thresholdFormShowsErrorsAndDisablesSave() {
        val state = SettingsUiState("9,0", "8,0", "0,0", setOf(ThresholdError.MIN_NOT_BELOW_MAX))
        compose.setContent { FrostlineTheme { ThresholdSection(state, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.settings_error_min_max)).assertExists()
        compose.onNodeWithText(text(R.string.settings_threshold_note)).assertExists()
        compose.onNodeWithText(text(R.string.settings_save)).assertIsNotEnabled()
    }

    @Test
    fun aValidThresholdFormCanBeSaved() {
        compose.setContent { FrostlineTheme { ThresholdSection(SettingsUiState("2,0", "8,0", "0,0"), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.settings_save)).assertIsEnabled()
    }
}
