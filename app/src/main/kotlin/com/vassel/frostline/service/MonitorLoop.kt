// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.SensorAdapter
import com.vassel.frostline.domain.port.SensorEvent
import com.vassel.frostline.domain.usecase.LogReading
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Receives alarm events from the loop. */
fun interface AlarmSink {
    fun notify(event: AlarmEvent)
}

/**
 * The monitoring logic without any Android type: collects a sensor, throttles samples to the log interval, logs them
 * through [LogReading] (which runs the excursion engine) and runs the sensor watchdog. Cancelling stops everything,
 * including the sensor connection. A failure in [LogReading] (for example the database) propagates to the caller.
 */
class MonitorLoop(
    private val tripId: String,
    private val logIntervalMs: Long,
    private val adapter: SensorAdapter,
    private val logReading: LogReading,
    private val clock: Clock,
    private val alarms: AlarmSink,
    private val status: MutableStateFlow<MonitorStatus>,
    private val watchdogPeriodMs: Long = 5_000,
) {
    private var lastLoggedElapsedMs: Long? = null

    suspend fun run() = coroutineScope {
        val watchdog = launch {
            while (true) {
                delay(watchdogPeriodMs)
                deliver(logReading.onTick(tripId))
            }
        }
        try {
            adapter.events().collect(::handle)
        } finally {
            watchdog.cancel()
        }
    }

    private suspend fun handle(event: SensorEvent) {
        when (event) {
            is SensorEvent.Connection -> status.update {
                it.copy(connection = event.state, failure = if (event.state == ConnectionState.CONNECTED) null else it.failure)
            }
            is SensorEvent.Failure -> status.update { it.copy(failure = event.reason) }
            is SensorEvent.Sample -> onSample(event)
        }
    }

    private suspend fun onSample(sample: SensorEvent.Sample) {
        val elapsed = clock.elapsedRealtimeMs()
        val last = lastLoggedElapsedMs
        if (last != null && elapsed - last < logIntervalMs) return
        lastLoggedElapsedMs = elapsed
        val now = clock.nowUtcMs()
        val events = logReading(tripId, Reading(now, elapsed, sample.tempC, sample.humidityPct))
        status.update { it.copy(lastTempC = sample.tempC, lastReadingUtcMs = now, sensorLost = false) }
        deliver(events)
    }

    private fun deliver(events: List<AlarmEvent>) {
        for (e in events) {
            if (e is AlarmEvent.SensorLost) status.update { it.copy(sensorLost = true) }
            if (e is AlarmEvent.SensorRecovered) status.update { it.copy(sensorLost = false) }
            alarms.notify(e)
        }
    }
}
