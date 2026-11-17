// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.usecase

import com.vassel.frostline.domain.engine.ExcursionEngine
import com.vassel.frostline.domain.engine.TripStatsCalculator
import com.vassel.frostline.domain.engine.Weighting
import com.vassel.frostline.domain.integrity.Canonicalizer
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.ChainVerifier
import com.vassel.frostline.domain.integrity.ClockJump
import com.vassel.frostline.domain.integrity.ClockJumpDetector
import com.vassel.frostline.domain.integrity.CustodyDiagnostic
import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.model.AlarmEvent
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.GapInfo
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStats
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.port.TransactionRunner
import com.vassel.frostline.domain.port.TripRepository
import java.security.PublicKey
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private suspend fun TripRepository.requireActive(tripId: String): Trip {
    val trip = checkNotNull(get(tripId)) { "Unknown trip" }
    check(trip.status == TripStatus.ACTIVE) { "Trip is already completed" }
    return trip
}

class StartTrip(
    private val trips: TripRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(
        carrierId: String?,
        operatorId: String,
        logIntervalSec: Int = 60,
        thresholds: ThresholdProfile = ThresholdProfile(),
        sensorId: String? = null,
    ): Trip {
        check(trips.getActive() == null) { "Another trip is still active" }
        val trip = Trip(newId(), carrierId, operatorId, clock.nowUtcMs(), null, TripStatus.ACTIVE, logIntervalSec, thresholds, sensorId)
        trips.create(trip)
        return trip
    }
}

/** Stores a reading and runs the excursion engine. The engine is rebuilt from stored readings after a restart. */
class LogReading(
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val excursions: ExcursionRepository,
    private val clock: Clock,
) {
    private val lock = Mutex()
    private var cached: Pair<String, ExcursionEngine>? = null

    suspend operator fun invoke(tripId: String, reading: Reading): List<AlarmEvent> = lock.withLock {
        val trip = trips.requireActive(tripId)
        val engine = engineFor(trip)
        val events = engine.onReading(readings.append(tripId, reading)) // analyse the reading exactly as stored
        val changed = events.mapNotNull { (it as? AlarmEvent.ExcursionOpened)?.excursion ?: (it as? AlarmEvent.ExcursionClosed)?.excursion }
        (changed + listOfNotNull(engine.snapshot().open)).distinctBy { it.id to it.endUtcMs }.forEach { excursions.upsert(it) }
        events
    }

    /** Sensor watchdog: reports SensorLost once when no reading arrived for longer than the gap threshold. */
    suspend fun onTick(tripId: String): List<AlarmEvent> = lock.withLock {
        engineFor(trips.requireActive(tripId)).onTick()
    }

    private suspend fun engineFor(trip: Trip): ExcursionEngine =
        cached?.takeIf { it.first == trip.id }?.second
            ?: ExcursionEngine.rebuild(trip.id, trip.thresholds, trip.logIntervalSec, clock, trip.startedUtcMs, readings.getAll(trip.id))
                .also { cached = trip.id to it }
}

class RecordCustodyEvent(
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val custody: CustodyRepository,
    private val excursions: ExcursionRepository,
    private val location: LocationProvider,
    private val signer: Signer,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    /** PICKUP or HANDOFF. DELIVERY goes through [CompleteTrip]. */
    suspend operator fun invoke(
        tripId: String, type: CustodyType, tagUid: String?, tagLabel: String?, userId: String,
        location: LocationResult? = null, tagUidHash: String? = null,
    ): CustodyEvent {
        require(type != CustodyType.DELIVERY) { "Use CompleteTrip for DELIVERY" }
        return record(tripId, type, tagUid, tagLabel, userId, location, tagUidHash)
    }

    /** The previous event (seq, prev hash) is read inside the same transaction as the insert, so concurrent calls cannot fork the chain. */
    internal suspend fun record(
        tripId: String, type: CustodyType, tagUid: String?, tagLabel: String?, userId: String,
        location: LocationResult?, tagUidHash: String?,
    ): CustodyEvent {
        // [location] is normally the fix taken at the tap; without one it is fetched here, outside the transaction (slow).
        val result = location ?: this.location.currentLocation()
        return transactions.run { recordInTransaction(tripId, type, tagUid, tagLabel, userId, result, tagUidHash) }
    }

    private suspend fun recordInTransaction(
        tripId: String, type: CustodyType, tagUid: String?, tagLabel: String?, userId: String,
        result: LocationResult, tagUidHash: String?,
    ): CustodyEvent {
        val fix: GeoLocation? = (result as? LocationResult.Fix)?.location
        trips.requireActive(tripId)
        val ts = clock.nowUtcMs()
        val upTo = readings.getUpTo(tripId, ts)
        val fields = CustodyFields(
            tripId, type, tagUid, tagLabel, ts, fix?.lat, fix?.lon, fix?.accuracyM, userId,
            tempCenti = upTo.maxByOrNull { it.tsUtcMs }?.tempCenti,
            excursionOpen = excursions.getAll(tripId).any { it.isOpen },
            readingsDigest = Canonicalizer.readingsDigest(upTo),
        )
        val event = HashChain.appendEvent(custody.getLast(tripId), fields, signer)
        custody.append(event, CustodyDiagnostic.of(tagUidHash, result))
        return event
    }
}

/**
 * Records DELIVERY, closes any open excursion at the delivery time and completes the trip, all in one transaction.
 * Nothing can be added afterwards.
 */
class CompleteTrip(
    private val record: RecordCustodyEvent,
    private val trips: TripRepository,
    private val excursions: ExcursionRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(
        tripId: String, tagUid: String?, tagLabel: String?, userId: String,
        location: LocationResult? = null, tagUidHash: String? = null,
    ): CustodyEvent =
        transactions.run {
            val event = record.record(tripId, CustodyType.DELIVERY, tagUid, tagLabel, userId, location, tagUidHash)
            excursions.getAll(tripId).filter { it.isOpen }.forEach { excursions.upsert(it.copy(endUtcMs = event.tsUtcMs)) }
            trips.markCompleted(tripId, event.tsUtcMs)
            event
        }
}

/**
 * Everything a report needs, without any rendering. The sensor appears only as [sensorMacHash]: the raw address never
 * leaves the encrypted database.
 */
data class ReportData(
    val trip: Trip,
    val carrier: Carrier?,
    val sensorMacHash: String?,
    val sensorModel: String?,
    val readings: List<Reading>,
    val excursions: List<Excursion>,
    val custody: List<CustodyEvent>,
    val stats: TripStats,
    val gaps: List<GapInfo>,
    val clockJumps: List<ClockJump>,
    val chainVerification: ChainVerification,
    /** [Canonicalizer.readingsDigest] over every reading of the trip. */
    val readingsDigest: String,
)

class BuildReportData(
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val excursions: ExcursionRepository,
    private val custody: CustodyRepository,
    private val carriers: CarrierRepository,
    private val sensors: SensorRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(tripId: String, publicKey: PublicKey): ReportData {
        val trip = checkNotNull(trips.get(tripId)) { "Unknown trip" }
        val rs = readings.getAll(tripId)
        val ex = excursions.getAll(tripId)
        val events = custody.getAll(tripId)
        val asOf = trip.endedUtcMs ?: clock.nowUtcMs()
        val sensor = trip.sensorId?.let { sensors.get(it) }
        return ReportData(
            trip = trip,
            carrier = trip.carrierId?.let { id -> carriers.getAll().firstOrNull { it.id == id } },
            sensorMacHash = sensor?.macHash,
            sensorModel = sensor?.model,
            readings = rs,
            excursions = ex,
            custody = events,
            stats = TripStatsCalculator.compute(rs, ex, trip.logIntervalSec, asOf),
            gaps = Weighting.analyze(rs, trip.logIntervalSec).gaps,
            clockJumps = ClockJumpDetector.detect(rs),
            chainVerification = ChainVerifier.verify(events, publicKey),
            readingsDigest = Canonicalizer.readingsDigest(rs),
        )
    }
}
