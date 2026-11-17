// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

enum class OnboardingStep { WELCOME, BLUETOOTH, NOTIFICATIONS, LOCATION, OPERATOR, DISCLAIMER }

/** The first-run sequence. Each permission is asked on its own step with a reason, and never all at once. */
object OnboardingSteps {
    fun forSdk(sdk: Int): List<OnboardingStep> =
        OnboardingStep.entries.filter { it != OnboardingStep.NOTIFICATIONS || sdk >= 33 }

    fun permissions(step: OnboardingStep, sdk: Int): List<String> = when (step) {
        OnboardingStep.BLUETOOTH ->
            if (sdk >= 31) listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT") else listOf("android.permission.ACCESS_FINE_LOCATION")
        OnboardingStep.NOTIFICATIONS -> listOf("android.permission.POST_NOTIFICATIONS")
        OnboardingStep.LOCATION -> listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
        else -> emptyList()
    }
}
