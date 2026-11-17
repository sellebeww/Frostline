// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.port

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import java.security.PublicKey
import kotlinx.coroutines.flow.Flow

/** Signs bytes with the device key (SHA256withECDSA in production). */
interface Signer {
    fun sign(data: ByteArray): ByteArray
}

/** The public half of the signing key, X.509 encoded by [PublicKey.getEncoded]. */
interface PublicKeySource {
    fun publicKey(): PublicKey
}

/** Runs [block] atomically: all repository writes inside commit together or not at all. Calls may nest. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

interface Clock {
    fun nowUtcMs(): Long
    fun elapsedRealtimeMs(): Long
}

interface ReadingRepository {
    /**
     * Stores [reading] and returns it as stored. A custody event commits to the digest of every reading up to its
     * timestamp, so a reading stamped at or before the latest custody event (a late arrival, or the wall clock set
     * backwards) is stored with its timestamp raised to one millisecond after that event. Nothing is dropped: the
     * temperature still counts, and the clock change still shows as a clock jump against the monotonic time.
     */
    suspend fun append(tripId: String, reading: Reading): Reading
    /** In capture (insertion) order, which is what clock jump detection needs. */
    suspend fun getAll(tripId: String): List<Reading>
    suspend fun getUpTo(tripId: String, tsUtcMsInclusive: Long): List<Reading>
}

interface TripRepository {
    suspend fun create(trip: Trip)
    suspend fun get(id: String): Trip?
    suspend fun getActive(): Trip?
    suspend fun markCompleted(id: String, endedUtcMs: Long)

    /** Newest first. */
    suspend fun getAll(): List<Trip>
}

/** Append-only by contract: there is deliberately no update or delete. */
interface CustodyRepository {
    /** [diagnostic] is unsigned side information (see CustodyDiagnostic); it is never part of the hash or signature. */
    suspend fun append(event: CustodyEvent, diagnostic: String?)
    suspend fun getAll(tripId: String): List<CustodyEvent>
    suspend fun getLast(tripId: String): CustodyEvent?
}

interface ExcursionRepository {
    suspend fun upsert(excursion: Excursion)
    suspend fun getAll(tripId: String): List<Excursion>
}

interface CarrierRepository {
    suspend fun getAll(): List<Carrier>
    suspend fun create(name: String, capacityLiters: Double?): Carrier
}

/** The profile new trips start with. Each trip keeps its own snapshot, so editing it never changes a past trip. */
interface ThresholdRepository {
    suspend fun getDefault(): ThresholdProfile
    suspend fun saveDefault(profile: ThresholdProfile)
}

data class GeoLocation(val lat: Double, val lon: Double, val accuracyM: Double, val tsUtcMs: Long)

enum class LocationFailure { PERMISSION_DENIED, TIMEOUT, UNAVAILABLE }

sealed interface LocationResult {
    /** [approximate] is true when the fix is a recent cached one instead of a fresh reading. */
    data class Fix(val location: GeoLocation, val approximate: Boolean = false) : LocationResult
    data class None(val reason: LocationFailure) : LocationResult
}

interface LocationProvider {
    /** Only called at an NFC tap with the screen on. Never throws for missing permission or a missing fix. */
    suspend fun currentLocation(): LocationResult
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/** Why a sensor cannot deliver data. NO_* are permanent for this device, the others can be fixed by the user. */
enum class SensorFailure { NO_ESS_SERVICE, NO_TEMPERATURE_CHARACTERISTIC, BLUETOOTH_OFF, PERMISSION_DENIED }

sealed interface SensorEvent {
    data class Connection(val state: ConnectionState) : SensorEvent

    /** A valid sample. Invalid ("unknown") values are dropped by the adapter and never become events. */
    data class Sample(val tempC: Double, val humidityPct: Double?) : SensorEvent

    data class Failure(val reason: SensorFailure) : SensorEvent
}

/**
 * Cold flow: collecting connects (and reconnects after drops), cancelling disconnects and releases every resource.
 * A flow that completes means the sensor ended for good (permanent failure or end of a simulation).
 */
interface SensorAdapter {
    fun events(): Flow<SensorEvent>
}

interface SensorRepository {
    /** Pairs [address]. Pairing the same device again returns the existing record and makes it the paired one. */
    suspend fun pair(address: String, model: String?, nowUtcMs: Long): Sensor

    /** The most recently paired sensor (the MVP supports one at a time). */
    suspend fun getPaired(): Sensor?

    suspend fun get(id: String): Sensor?
}

/** A generated report kept as a trace: which files, and their hashes. [path] is relative to the app files directory. */
data class ReportRecord(
    val id: String,
    val tripId: String,
    val pdfSha256: String,
    val manifestSha256: String,
    val createdAtUtcMs: Long,
    val path: String,
)

interface ReportRepository {
    suspend fun record(report: ReportRecord)

    /** Newest first. */
    suspend fun getAll(tripId: String): List<ReportRecord>
}
