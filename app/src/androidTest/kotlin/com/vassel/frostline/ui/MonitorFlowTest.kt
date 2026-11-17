// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui

import android.Manifest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.vassel.frostline.MainActivity
import com.vassel.frostline.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole MVP flow on a device, debug build, no hardware: onboarding, simulated sensor, trip, simulated tag taps
 * (PICKUP, HANDOFF, DELIVERY), history and detail with a valid custody chain. It uses the app's real database, so run it
 * on a device whose app data was cleared.
 */
@RunWith(AndroidJUnit4::class)
class MonitorFlowTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 120_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun click(id: Int) {
        waitFor(hasText(text(id)))
        compose.onNodeWithText(text(id)).performClick()
    }

    private fun present(id: Int) = compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()

    private fun tap(label: String) {
        Thread.sleep(3_500) // the 3 second tap debounce
        waitFor(hasText(label))
        compose.onNodeWithText(label).performClick()
        // the confirm button stays disabled until the location lookup finishes
        waitFor(hasText(text(R.string.handover_confirm)) and isEnabled())
        click(R.string.handover_confirm)
    }

    @Test
    fun fullTripFromOnboardingToDeliveryAndDetail() {
        // onboarding (first run only)
        waitFor(hasText(text(R.string.onboarding_welcome_title)) or hasText(text(R.string.home_disclaimer), substring = true))
        if (present(R.string.onboarding_welcome_title)) {
            click(R.string.onboarding_next)
            // every permission is granted by the rule, so the permission steps skip themselves
            waitFor(hasText(text(R.string.operator_save)))
            compose.onNodeWithText(text(R.string.operator_name)).performTextInput("Penguji")
            click(R.string.operator_save)
            click(R.string.onboarding_finish)
        }

        // start a trip with the simulated sensor
        click(R.string.home_start_trip)
        click(R.string.home_pick_sensor)
        val sim = text(R.string.sim_stable)
        waitFor(hasText(sim))
        compose.onNodeWithText(sim).performClick()
        waitFor(hasText(text(R.string.home_change_sensor))) // the screen shows the paired sensor
        click(R.string.start_trip_go)

        // Home: the monitor is running and shows the temperature and the safe status
        waitFor(hasText(text(R.string.status_safe)))
        waitFor(hasText("5,0", substring = true))

        // handover with simulated taps
        click(R.string.home_handover)
        tap("Tag simulasi 1")
        waitFor(hasText("PICKUP", substring = true))
        tap("Tag simulasi 2")
        waitFor(hasText("HANDOFF", substring = true))
        Thread.sleep(3_500)
        compose.onNodeWithText("Tag simulasi 3").performClick()
        waitFor(isToggleable())
        compose.onNode(isToggleable()).performClick()
        waitFor(hasText(text(R.string.handover_confirm)) and isEnabled())
        click(R.string.handover_confirm)
        waitFor(hasText(text(R.string.handover_trip_done)))

        // history and detail
        compose.onAllNodesWithText(text(R.string.nav_trips))[0].performClick()
        waitFor(hasText(text(R.string.trip_status_done), substring = true))
        compose.onNode(hasText(text(R.string.trip_status_done), substring = true)).performClick()
        waitFor(hasText(text(R.string.detail_chain_valid)))
    }
}
