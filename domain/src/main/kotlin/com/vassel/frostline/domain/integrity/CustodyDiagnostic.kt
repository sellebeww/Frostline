// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.port.LocationResult

/**
 * Side information stored next to a custody event for diagnostics (hashed tag UID, how the location was obtained).
 * It is NOT signed: it can be edited without breaking the chain, so it must never be used as evidence.
 */
object CustodyDiagnostic {
    fun of(tagUidHash: String?, location: LocationResult): String {
        val loc = when (location) {
            is LocationResult.Fix -> if (location.approximate) "approximate" else "ok"
            is LocationResult.None -> "none:${location.reason.name}"
        }
        return listOfNotNull(tagUidHash?.let { "uid=$it" }, "loc=$loc").joinToString(";")
    }
}
