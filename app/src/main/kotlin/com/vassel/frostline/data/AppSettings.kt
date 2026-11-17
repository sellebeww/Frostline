// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AppSettings {
    /** Seconds between logged readings, always within [MIN_INTERVAL_SEC]..[MAX_INTERVAL_SEC]. */
    var logIntervalSec: Int
    var onboardingDone: Boolean

    companion object {
        const val MIN_INTERVAL_SEC = 10
        const val MAX_INTERVAL_SEC = 600
        const val DEFAULT_INTERVAL_SEC = 60
    }
}

@Singleton
class PrefsAppSettings @Inject constructor(@ApplicationContext context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences("frostline_settings", Context.MODE_PRIVATE)

    override var logIntervalSec: Int
        get() = prefs.getInt(KEY_INTERVAL, AppSettings.DEFAULT_INTERVAL_SEC).coerceIn(AppSettings.MIN_INTERVAL_SEC, AppSettings.MAX_INTERVAL_SEC)
        set(value) = prefs.edit { putInt(KEY_INTERVAL, value.coerceIn(AppSettings.MIN_INTERVAL_SEC, AppSettings.MAX_INTERVAL_SEC)) }

    override var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    private companion object {
        const val KEY_INTERVAL = "log_interval_sec"
        const val KEY_ONBOARDING = "onboarding_done"
    }
}
