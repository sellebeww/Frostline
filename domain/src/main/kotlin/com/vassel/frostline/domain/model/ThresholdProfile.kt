// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

/** Defaults are not a clinical standard. */
data class ThresholdProfile(
    val minC: Double = 2.0,
    val maxC: Double = 8.0,
    val freezeC: Double = 0.0,
) {
    init {
        require(freezeC < minC && minC < maxC) { "Require freezeC < minC < maxC" }
    }
}
