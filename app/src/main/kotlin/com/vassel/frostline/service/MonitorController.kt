// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Starts and stops the monitoring service. An interface so that ViewModels can be tested without Android. */
interface MonitorController {
    /** Call only while the app is visible. False when Android refused. */
    fun start(): Boolean
    fun stop()
}

class AndroidMonitorController @Inject constructor(@ApplicationContext private val context: Context) : MonitorController {
    override fun start() = FrostlineMonitorService.start(context)
    override fun stop() = FrostlineMonitorService.stop(context)
}
