// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vassel.frostline.MainActivity
import com.vassel.frostline.R
import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.service.AlarmSink
import com.vassel.frostline.service.FrostlineMonitorService
import com.vassel.frostline.service.MonitorEnd
import com.vassel.frostline.service.MonitorStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifier @Inject constructor(@ApplicationContext private val context: Context) : AlarmSink {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW),
        )
        val alarm = NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 800)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        manager.createNotificationChannel(alarm)
    }

    /** The persistent notification of the foreground service. */
    fun foreground(status: MonitorStatus): Notification {
        val text = when {
            status.sensorLost -> context.getString(R.string.monitor_sensor_lost)
            status.lastTempC == null -> context.getString(R.string.monitor_waiting)
            else -> context.getString(R.string.monitor_last_temp, formatTemp(status.lastTempC), connectionText(status.connection))
        }
        val stop = PendingIntent.getService(
            context, 1, Intent(context, FrostlineMonitorService::class.java).setAction(FrostlineMonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_frostline)
            .setContentTitle(context.getString(R.string.monitor_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.monitor_stop), stop)
            .build()
    }

    fun updateForeground(status: MonitorStatus) = post(ID_FOREGROUND, foreground(status))

    override fun notify(event: AlarmEvent) {
        when (event) {
            is AlarmEvent.ExcursionOpened -> alarm(
                ID_EXCURSION,
                if (event.excursion.type == ExcursionType.HEAT) R.string.alarm_heat_title else R.string.alarm_cold_title,
                context.getString(R.string.alarm_peak, formatTemp(event.excursion.peakC)),
            )
            is AlarmEvent.FreezeRisk -> alarm(ID_FREEZE, R.string.alarm_freeze_title, context.getString(R.string.alarm_peak, formatTemp(event.excursion.peakC)))
            is AlarmEvent.ExcursionClosed -> info(ID_EXCURSION, R.string.alarm_closed_title, context.getString(R.string.alarm_closed_text))
            is AlarmEvent.SensorLost -> alarm(ID_SENSOR, R.string.alarm_sensor_lost_title, context.getString(R.string.alarm_sensor_lost_text))
            is AlarmEvent.SensorRecovered -> info(ID_SENSOR, R.string.alarm_sensor_recovered_title, null)
        }
    }

    /** Android refused to start or keep the foreground service. Tapping opens the app, where it can be resumed. */
    fun postStartBlocked() = info(ID_SERVICE_STATE, R.string.blocked_title, context.getString(R.string.blocked_text))

    fun postStopped(reason: MonitorEnd) {
        val text = when (reason) {
            MonitorEnd.SENSOR_ENDED -> R.string.stopped_sensor_ended
            MonitorEnd.NO_ACTIVE_TRIP -> R.string.stopped_no_trip
            MonitorEnd.NO_SENSOR -> R.string.stopped_no_sensor
            MonitorEnd.DATABASE_UNAVAILABLE -> R.string.stopped_database
            MonitorEnd.TRIP_COMPLETED -> R.string.stopped_trip_completed
            MonitorEnd.ERROR -> R.string.stopped_error
        }
        info(ID_SERVICE_STATE, R.string.stopped_title, context.getString(text))
    }

    fun clearServiceState() = manager.cancel(ID_SERVICE_STATE)

    private fun alarm(id: Int, title: Int, text: String) = post(id, build(CHANNEL_ALARM, title, text, NotificationCompat.PRIORITY_MAX, NotificationCompat.CATEGORY_ALARM))

    private fun info(id: Int, title: Int, text: String?) = post(id, build(CHANNEL_ALARM, title, text, NotificationCompat.PRIORITY_DEFAULT, NotificationCompat.CATEGORY_STATUS))

    private fun build(channel: String, title: Int, text: String?, priority: Int, category: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_frostline)
            .setContentTitle(context.getString(title))
            .apply { if (text != null) setContentText(text) }
            .setPriority(priority)
            .setCategory(category)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_open), openApp()) // heads-up alarms carry an explicit "Buka" action
            .build()

    private fun post(id: Int, notification: Notification) {
        // Without the permission (Android 13+) the system drops the notification; the foreground service still runs.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        manager.notify(id, notification)
    }

    private fun openApp() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun connectionText(state: ConnectionState) = context.getString(
        when (state) {
            ConnectionState.CONNECTED -> R.string.conn_connected
            ConnectionState.CONNECTING -> R.string.conn_connecting
            ConnectionState.DISCONNECTED -> R.string.conn_disconnected
        },
    )

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALARM = "alarm"
        const val ID_FOREGROUND = 1
        private const val ID_EXCURSION = 2
        private const val ID_FREEZE = 3
        private const val ID_SENSOR = 4
        private const val ID_SERVICE_STATE = 5

        /** Indonesian decimal comma, one decimal. For display only. */
        fun formatTemp(value: Double): String = String.format(Locale.forLanguageTag("id-ID"), "%.1f", value)
    }
}
