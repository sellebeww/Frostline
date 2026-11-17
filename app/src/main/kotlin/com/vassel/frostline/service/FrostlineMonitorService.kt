// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vassel.frostline.data.db.DatabaseState
import com.vassel.frostline.di.DataEntryPoint
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.usecase.LogReading
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service (type connectedDevice) that hosts a [MonitorLoop] for the active trip. START_STICKY: if the system
 * kills the process the service is restarted with a null intent, finds the ACTIVE trip in the database and rebuilds the
 * excursion state from the stored readings. If Android refuses to run it in the background, it posts a notification
 * that reopens the app instead of failing silently.
 */
class FrostlineMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val entry by lazy { EntryPointAccessors.fromApplication(applicationContext, DataEntryPoint::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) monitorJob = scope.launch { monitor() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel() // cancels the loop, which closes the sensor connection
        entry.statusStore().state.value = entry.statusStore().state.value.copy(running = false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val notifier = entry.alarmNotifier()
        return try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            ServiceCompat.startForeground(this, com.vassel.frostline.notification.AlarmNotifier.ID_FOREGROUND, notifier.foreground(entry.statusStore().status.value), type)
            true
        } catch (_: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException (Android 12+)
            notifier.postStartBlocked()
            false
        } catch (_: SecurityException) { // for example Bluetooth permission missing for type connectedDevice (Android 14)
            notifier.postStartBlocked()
            false
        }
    }

    private suspend fun monitor() {
        val notifier = entry.alarmNotifier()
        val store = entry.statusStore()
        val statusJob = scope.launch { store.status.collect { if (it.running) notifier.updateForeground(it) } }
        val end = try {
            run(store)
        } catch (e: CancellationException) {
            throw e // onDestroy resets the status
        } catch (_: Exception) {
            MonitorEnd.ERROR
        } finally {
            statusJob.cancel()
        }
        store.state.value = store.state.value.copy(running = false, ended = end)
        notifier.postStopped(end)
        stopSelf()
    }

    /** Returns why the loop ended. Throws only for unexpected errors (mapped to ERROR by the caller). */
    private suspend fun run(store: MonitorStatusStore): MonitorEnd {
        val state = withContext(Dispatchers.IO) { entry.databaseState() }
        if (state is DatabaseState.Failed) return MonitorEnd.DATABASE_UNAVAILABLE
        val trip = entry.trips().getActive() ?: return MonitorEnd.NO_ACTIVE_TRIP
        val sensor = trip.sensorId?.let { entry.sensors().get(it) } ?: return MonitorEnd.NO_SENSOR
        val session = entry.sensorBackend().open(sensor.address, trip.startedUtcMs)
        store.state.value = MonitorStatus(running = true, tripId = trip.id)
        entry.alarmNotifier().clearServiceState()
        try {
            MonitorLoop(
                trip.id, trip.logIntervalSec * 1000L, session.adapter,
                LogReading(entry.trips(), entry.readings(), entry.excursions(), session.clock),
                session.clock, entry.alarmNotifier(), store.state,
            ).run()
        } catch (e: IllegalStateException) {
            // DELIVERY completed the trip while a reading was in flight: a normal end, not an error
            if (entry.trips().get(trip.id)?.status == TripStatus.COMPLETED) return MonitorEnd.TRIP_COMPLETED
            throw e
        }
        return MonitorEnd.SENSOR_ENDED
    }

    companion object {
        const val ACTION_STOP = "com.vassel.frostline.action.STOP_MONITOR"

        /** Call only while the app is visible: Android 12+ forbids starting a foreground service from the background. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, FrostlineMonitorService::class.java))
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FrostlineMonitorService::class.java))
        }
    }
}
