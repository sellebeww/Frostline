// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormsAndStepsTest {
    private fun errors(min: String, max: String, freeze: String) = assertIs<ThresholdResult.Invalid>(ThresholdForm.validate(min, max, freeze)).errors

    @Test
    fun theDefaultsAreValidAndAcceptACommaOrAPoint() {
        assertIs<ThresholdResult.Valid>(ThresholdForm.validate("2,0", "8,0", "0,0"))
        val p = assertIs<ThresholdResult.Valid>(ThresholdForm.validate(" 2.5 ", "7,5", "-0,5")).profile
        assertEquals(2.5, p.minC); assertEquals(7.5, p.maxC); assertEquals(-0.5, p.freezeC)
    }

    @Test
    fun minMustBeBelowMax() {
        assertEquals(setOf(ThresholdError.MIN_NOT_BELOW_MAX), errors("8", "8", "0"))
        assertEquals(setOf(ThresholdError.MIN_NOT_BELOW_MAX), errors("9", "8", "0"))
    }

    @Test
    fun freezeMustBeBelowMin() {
        assertEquals(setOf(ThresholdError.FREEZE_NOT_BELOW_MIN), errors("2", "8", "2"))
        assertEquals(setOf(ThresholdError.FREEZE_NOT_BELOW_MIN), errors("2", "8", "3"))
    }

    @Test
    fun nonNumbersAndAbsurdValuesAreRejected() {
        assertEquals(setOf(ThresholdError.MIN_INVALID), errors("", "8", "0"))
        assertEquals(setOf(ThresholdError.MAX_INVALID), errors("2", "abc", "0"))
        assertEquals(setOf(ThresholdError.FREEZE_INVALID), errors("2", "8", "NaN"))
        assertEquals(setOf(ThresholdError.MAX_INVALID), errors("2", "999", "0"))
        assertNull(ThresholdForm.parse("1e400"))
    }

    @Test
    fun everythingWrongAtOnceReportsEachProblem() {
        assertEquals(setOf(ThresholdError.MIN_NOT_BELOW_MAX, ThresholdError.FREEZE_NOT_BELOW_MIN), errors("9", "3", "9"))
    }

    @Test
    fun notificationsStepExistsOnlyFromAndroid13() {
        assertFalse(OnboardingStep.NOTIFICATIONS in OnboardingSteps.forSdk(32))
        assertTrue(OnboardingStep.NOTIFICATIONS in OnboardingSteps.forSdk(33))
        assertEquals(OnboardingStep.WELCOME, OnboardingSteps.forSdk(34).first())
        assertEquals(OnboardingStep.DISCLAIMER, OnboardingSteps.forSdk(34).last())
    }

    @Test
    fun eachPermissionIsAskedOnItsOwnStepWithTheRightSet() {
        assertEquals(listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"), OnboardingSteps.permissions(OnboardingStep.BLUETOOTH, 34))
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), OnboardingSteps.permissions(OnboardingStep.BLUETOOTH, 30))
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), OnboardingSteps.permissions(OnboardingStep.NOTIFICATIONS, 34))
        assertEquals(2, OnboardingSteps.permissions(OnboardingStep.LOCATION, 34).size)
        assertEquals(emptyList(), OnboardingSteps.permissions(OnboardingStep.OPERATOR, 34))
    }

    @Test
    fun homeStatusPrioritisesSilenceThenFreezeThenHeatAndCold() {
        val open = com.vassel.frostline.domain.model.ExcursionType.values()
        assertEquals(HomeStatus.READY, homeStatus(false, false, false, null))
        assertEquals(HomeStatus.IDLE, homeStatus(true, false, false, open[0]))
        assertEquals(HomeStatus.SAFE, homeStatus(true, true, false, null))
        assertEquals(HomeStatus.SENSOR_LOST, homeStatus(true, true, true, com.vassel.frostline.domain.model.ExcursionType.FREEZE))
        assertEquals(HomeStatus.FREEZE, homeStatus(true, true, false, com.vassel.frostline.domain.model.ExcursionType.FREEZE))
        assertEquals(HomeStatus.HEAT, homeStatus(true, true, false, com.vassel.frostline.domain.model.ExcursionType.HEAT))
        assertEquals(HomeStatus.COLD, homeStatus(true, true, false, com.vassel.frostline.domain.model.ExcursionType.COLD))
    }
}
