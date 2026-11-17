// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import com.vassel.frostline.domain.model.ExcursionType

/** What the cooler is doing right now. Always shown as icon plus text, never colour alone. */
enum class HomeStatus { READY, IDLE, SAFE, HEAT, COLD, FREEZE, SENSOR_LOST }

/** A silent sensor outranks everything (the temperature is unknown); then freeze, heat, cold. */
fun homeStatus(hasTrip: Boolean, running: Boolean, sensorLost: Boolean, open: ExcursionType?): HomeStatus = when {
    !hasTrip -> HomeStatus.READY
    !running -> HomeStatus.IDLE
    sensorLost -> HomeStatus.SENSOR_LOST
    open == ExcursionType.FREEZE -> HomeStatus.FREEZE
    open == ExcursionType.HEAT -> HomeStatus.HEAT
    open == ExcursionType.COLD -> HomeStatus.COLD
    else -> HomeStatus.SAFE
}
