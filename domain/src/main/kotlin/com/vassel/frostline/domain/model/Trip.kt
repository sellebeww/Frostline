// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

enum class TripStatus { ACTIVE, COMPLETED }

data class Trip(
    val id: String,
    val carrierId: String?,
    val operatorId: String,
    val startedUtcMs: Long,
    val endedUtcMs: Long?,
    val status: TripStatus,
    val logIntervalSec: Int,
    val thresholds: ThresholdProfile,
    val sensorId: String? = null,
) {
    init {
        require(logIntervalSec in 10..600) { "logIntervalSec must be within 10..600" }
    }
}
