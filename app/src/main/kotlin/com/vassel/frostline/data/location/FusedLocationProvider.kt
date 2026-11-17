// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationFailure
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Location for an NFC tap: a high accuracy fix with a 10 s timeout, falling back to the last known location only when
 * it is younger than 2 minutes. Needs Google Play services; without them the result is UNAVAILABLE.
 * Never throws: every problem becomes a [LocationResult.None] with a reason.
 */
@SuppressLint("MissingPermission") // checked in granted()
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : LocationProvider {

    override suspend fun currentLocation(): LocationResult {
        if (!granted()) return LocationResult.None(LocationFailure.PERMISSION_DENIED)
        val client = LocationServices.getFusedLocationProviderClient(context)
        var timedOut = false
        val current = try {
            withTimeout(LocationPolicy.CURRENT_TIMEOUT_MS) { awaitCurrent(client) }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { // SecurityException, ApiException, Play services missing
            null
        }
        val last = if (current == null) awaitLast(client) else null
        return LocationPolicy.choose(current, last, clock.nowUtcMs(), timedOut)
    }

    private fun granted() =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private suspend fun awaitCurrent(client: FusedLocationProviderClient): GeoLocation? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationTokenSource()
            cont.invokeOnCancellation { cancel.cancel() }
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(LocationPolicy.CURRENT_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(0)
                .build()
            client.getCurrentLocation(request, cancel.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it?.toGeo()) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }

    private suspend fun awaitLast(client: FusedLocationProviderClient): GeoLocation? =
        suspendCancellableCoroutine { cont ->
            try {
                client.lastLocation
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it?.toGeo()) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }

    private fun Location.toGeo() = GeoLocation(latitude, longitude, accuracy.toDouble(), time)
}
