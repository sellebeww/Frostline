// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.SensorFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Why the monitor stopped by itself. */
enum class MonitorEnd { SENSOR_ENDED, NO_ACTIVE_TRIP, NO_SENSOR, DATABASE_UNAVAILABLE, TRIP_COMPLETED, ERROR }

/** What the UI and the foreground notification show. [lastTempC] is the last value that was logged. */
data class MonitorStatus(
    val running: Boolean = false,
    val tripId: String? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val lastTempC: Double? = null,
    val lastReadingUtcMs: Long? = null,
    val sensorLost: Boolean = false,
    val failure: SensorFailure? = null,
    val ended: MonitorEnd? = null,
)

/** Process-wide state shared between the service and the UI. */
@Singleton
class MonitorStatusStore @Inject constructor() {
    val state = MutableStateFlow(MonitorStatus())
    val status: StateFlow<MonitorStatus> get() = state
}
