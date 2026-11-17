// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

sealed interface AlarmEvent {
    data class ExcursionOpened(val excursion: Excursion) : AlarmEvent
    data class FreezeRisk(val excursion: Excursion) : AlarmEvent
    data class ExcursionClosed(val excursion: Excursion) : AlarmEvent
    data class SensorLost(val tsUtcMs: Long, val lastReadingUtcMs: Long) : AlarmEvent
    data class SensorRecovered(val tsUtcMs: Long) : AlarmEvent
}
