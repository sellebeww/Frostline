// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

/** A span between two readings longer than the gap threshold. It is excluded from weighting. */
data class GapInfo(val startUtcMs: Long, val endUtcMs: Long) {
    val durationMs: Long get() = endUtcMs - startUtcMs
}

data class TripStats(
    val readingCount: Int,
    val minC: Double?,
    val maxC: Double?,
    val meanC: Double?,
    val mktC: Double?,
    val outOfRangeMs: Long,
    val excursionCount: Int,
    val longestExcursionMs: Long,
    val gapCount: Int,
    val gapTotalMs: Long,
)
