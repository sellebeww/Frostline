// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import com.vassel.frostline.domain.model.ThresholdProfile

enum class ThresholdError { MIN_INVALID, MAX_INVALID, FREEZE_INVALID, MIN_NOT_BELOW_MAX, FREEZE_NOT_BELOW_MIN }

sealed interface ThresholdResult {
    data class Valid(val profile: ThresholdProfile) : ThresholdResult
    data class Invalid(val errors: Set<ThresholdError>) : ThresholdResult
}

/** Validates what the operator typed. Accepts a decimal comma or point. Rules: min < max and freeze < min. */
object ThresholdForm {
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in -60.0..100.0 }

    fun validate(minText: String, maxText: String, freezeText: String): ThresholdResult {
        val min = parse(minText)
        val max = parse(maxText)
        val freeze = parse(freezeText)
        val errors = buildSet {
            if (min == null) add(ThresholdError.MIN_INVALID)
            if (max == null) add(ThresholdError.MAX_INVALID)
            if (freeze == null) add(ThresholdError.FREEZE_INVALID)
            if (min != null && max != null && min >= max) add(ThresholdError.MIN_NOT_BELOW_MAX)
            if (min != null && freeze != null && freeze >= min) add(ThresholdError.FREEZE_NOT_BELOW_MIN)
        }
        if (min == null || max == null || freeze == null || errors.isNotEmpty()) return ThresholdResult.Invalid(errors)
        return ThresholdResult.Valid(ThresholdProfile(min, max, freeze))
    }
}
