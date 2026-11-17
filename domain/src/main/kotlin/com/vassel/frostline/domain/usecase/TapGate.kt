// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.port.Clock

/** Ignores a tap that follows the previous accepted one within [debounceMs] (NFC readers often report a tag twice). */
class TapGate(private val clock: Clock, private val debounceMs: Long = 3_000) {
    private var lastAcceptedMs: Long? = null

    @Synchronized
    fun accept(): Boolean {
        val now = clock.elapsedRealtimeMs()
        val last = lastAcceptedMs
        if (last != null && now - last < debounceMs) return false
        lastAcceptedMs = now
        return true
    }
}
