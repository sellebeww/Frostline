// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.port.Signer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.robolectric.RuntimeEnvironment

/** In-memory Room with the framework SQLite (no SQLCipher): the injectable open helper factory is null. */
fun inMemoryDb(): FrostlineDatabase = FrostlineDatabase.create(RuntimeEnvironment.getApplication(), null, null)

fun newKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

class JcaSigner(private val pair: KeyPair = newKeyPair()) : Signer {
    val publicKey get() = pair.public
    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(data); sign() }
}

class TestClock(var now: Long = 1_000_000L) : Clock {
    override fun nowUtcMs() = now
    override fun elapsedRealtimeMs() = now
}

class TestLocation : LocationProvider {
    override suspend fun currentLocation(): LocationResult = LocationResult.Fix(GeoLocation(-6.2, 106.8, 5.0, 0L))
}

fun trip(id: String, status: TripStatus = TripStatus.ACTIVE, carrierId: String? = null) =
    Trip(id, carrierId, "op", 1_000L, if (status == TripStatus.COMPLETED) 2_000L else null, status, 60, ThresholdProfile())
