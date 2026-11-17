// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import androidx.room.withTransaction
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.MacHash
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ThresholdRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.ReportRecord
import com.vassel.frostline.domain.port.ReportRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.TransactionRunner
import com.vassel.frostline.domain.port.TripRepository
import java.util.UUID
import javax.inject.Inject

/** Room transaction. Nested calls join the outer transaction. */
class RoomTransactionRunner @Inject constructor(private val db: FrostlineDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

class RoomTripRepository @Inject constructor(private val db: FrostlineDatabase) : TripRepository {
    private val dao get() = db.tripDao()

    /** Each trip stores its own threshold snapshot, so later profile edits never change a past trip. */
    override suspend fun create(trip: Trip) = db.withTransaction {
        val profileId = UUID.randomUUID().toString()
        dao.insertProfile(trip.thresholds.toEntity(profileId, "trip-${trip.id}"))
        dao.insertTrip(trip.toEntity(profileId))
    }

    override suspend fun get(id: String): Trip? = dao.getTrip(id)?.let { toDomain(it) }

    override suspend fun getActive(): Trip? = dao.getActive()?.let { toDomain(it) }

    override suspend fun getAll(): List<Trip> = dao.getAll().map { toDomain(it) }

    override suspend fun markCompleted(id: String, endedUtcMs: Long) {
        check(dao.markCompleted(id, endedUtcMs) == 1) { "Trip does not exist or is not active" }
    }

    private suspend fun toDomain(entity: TripEntity): Trip {
        val profile = checkNotNull(dao.getProfile(entity.thresholdProfileId)) { "Missing threshold profile" }
        return entity.toDomain(profile)
    }
}

class RoomReadingRepository @Inject constructor(private val db: FrostlineDatabase) : ReadingRepository {
    /** The floor lookup and the insert share one transaction, which Room serialises with the custody transaction. */
    override suspend fun append(tripId: String, reading: Reading): Reading = db.withTransaction {
        val floor = db.custodyDao().getLastTs(tripId)?.plus(1)
        val stored = if (floor != null && reading.tsUtcMs < floor) reading.copy(tsUtcMs = floor) else reading
        db.readingDao().insert(stored.toEntity(tripId))
        stored
    }
    override suspend fun getAll(tripId: String) = db.readingDao().getAll(tripId).map { it.toDomain() }
    override suspend fun getUpTo(tripId: String, tsUtcMsInclusive: Long) =
        db.readingDao().getUpTo(tripId, tsUtcMsInclusive).map { it.toDomain() }
}

class RoomCustodyRepository @Inject constructor(private val db: FrostlineDatabase) : CustodyRepository {
    override suspend fun append(event: CustodyEvent, diagnostic: String?) = db.custodyDao().insert(event.toEntity(diagnostic))
    override suspend fun getAll(tripId: String) = db.custodyDao().getAll(tripId).map { it.toDomain() }
    override suspend fun getLast(tripId: String) = db.custodyDao().getLast(tripId)?.toDomain()
}

class RoomExcursionRepository @Inject constructor(private val db: FrostlineDatabase) : ExcursionRepository {
    override suspend fun upsert(excursion: Excursion) = db.excursionDao().upsert(excursion.toEntity())
    override suspend fun getAll(tripId: String) = db.excursionDao().getAll(tripId).map { it.toDomain() }
}

class RoomSensorRepository @Inject constructor(private val db: FrostlineDatabase) : SensorRepository {
    private val dao get() = db.sensorDao()

    override suspend fun pair(address: String, model: String?, nowUtcMs: Long): Sensor = db.withTransaction {
        val hash = MacHash.of(address)
        val existing = dao.findByHash(hash)
        if (existing == null) {
            dao.insert(SensorEntity(UUID.randomUUID().toString(), hash, model, address, nowUtcMs))
        } else {
            dao.repair(existing.id, nowUtcMs, model)
        }
        checkNotNull(dao.findByHash(hash)).toDomain()
    }

    override suspend fun getPaired(): Sensor? = dao.latest()?.toDomain()

    override suspend fun get(id: String): Sensor? = dao.get(id)?.toDomain()
}

class RoomCarrierRepository @Inject constructor(private val db: FrostlineDatabase) : CarrierRepository {
    override suspend fun getAll(): List<Carrier> = db.carrierDao().getAll().map { Carrier(it.id, it.name, it.capacityLiters, it.notes) }

    override suspend fun create(name: String, capacityLiters: Double?): Carrier {
        val entity = CarrierEntity(UUID.randomUUID().toString(), name.trim(), capacityLiters, null)
        db.carrierDao().insert(entity)
        return Carrier(entity.id, entity.name, entity.capacityLiters, entity.notes)
    }
}

class RoomThresholdRepository @Inject constructor(private val db: FrostlineDatabase) : ThresholdRepository {
    override suspend fun getDefault(): ThresholdProfile =
        db.thresholdDao().get(DEFAULT_ID)?.let { ThresholdProfile(it.minC, it.maxC, it.freezeC) } ?: ThresholdProfile()

    override suspend fun saveDefault(profile: ThresholdProfile) {
        db.thresholdDao().put(ThresholdProfileEntity(DEFAULT_ID, "default", profile.minC, profile.maxC, profile.freezeC))
    }

    private companion object {
        const val DEFAULT_ID = "00000000-0000-0000-0000-000000000001"
    }
}

class RoomReportRepository @Inject constructor(private val db: FrostlineDatabase) : ReportRepository {
    override suspend fun record(report: ReportRecord) =
        db.reportDao().insert(ReportEntity(report.id, report.tripId, report.pdfSha256, report.manifestSha256, report.createdAtUtcMs, report.path))

    override suspend fun getAll(tripId: String) = db.reportDao().getAll(tripId).map {
        ReportRecord(it.id, it.tripId, it.pdfSha256, it.manifestSha256, it.createdAtUtcMs, it.path)
    }
}
