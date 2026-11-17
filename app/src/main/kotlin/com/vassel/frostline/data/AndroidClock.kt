// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import android.os.SystemClock
import com.vassel.frostline.domain.port.Clock
import javax.inject.Inject

class AndroidClock @Inject constructor() : Clock {
    override fun nowUtcMs(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
