// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline

import android.app.Application
import com.vassel.frostline.di.DataEntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FrostlineApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val entry = EntryPoints.get(this, DataEntryPoint::class.java)
        entry.alarmNotifier().ensureChannels()
        // Open the encrypted database off the main thread so screens do not pay for it.
        appScope.launch { entry.databaseState() }
    }
}
