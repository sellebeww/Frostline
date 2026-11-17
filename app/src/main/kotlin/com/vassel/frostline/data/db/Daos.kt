// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CarrierDao {
    @Insert
    suspend fun insert(carrier: CarrierEntity)

    @Query("SELECT * FROM carriers ORDER BY name")
    suspend fun getAll(): List<CarrierEntity>
}

@Dao
interface TripDao {
    @Insert
    suspend fun insertProfile(profile: ThresholdProfileEntity)

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: String): TripEntity?

    @Query("SELECT * FROM threshold_profiles WHERE id = :id")
    suspend fun getProfile(id: String): ThresholdProfileEntity?

    @Query("SELECT * FROM trips ORDER BY started_utc_ms DESC")
    suspend fun getAll(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActive(): TripEntity?

    /** Returns the number of rows changed; 0 means the trip does not exist or is not ACTIVE. */
    @Query("UPDATE trips SET status = 'COMPLETED', ended_utc_ms = :endedUtcMs WHERE id = :id AND status = 'ACTIVE'")
    suspend fun markCompleted(id: String, endedUtcMs: Long): Int
}

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: ReadingEntity)

    /** Capture order (insertion order). */
    @Query("SELECT * FROM readings WHERE trip_id = :tripId ORDER BY rowid")
    suspend fun getAll(tripId: String): List<ReadingEntity>

    @Query("SELECT * FROM readings WHERE trip_id = :tripId AND ts_utc_ms <= :tsUtcMs ORDER BY rowid")
    suspend fun getUpTo(tripId: String, tsUtcMs: Long): List<ReadingEntity>
}

/** Deliberately insert and query only: custody events are append-only. */
@Dao
interface CustodyDao {
    @Insert
    suspend fun insert(event: CustodyEventEntity)

    @Query("SELECT * FROM custody_events WHERE trip_id = :tripId ORDER BY seq")
    suspend fun getAll(tripId: String): List<CustodyEventEntity>

    @Query("SELECT MAX(ts_utc_ms) FROM custody_events WHERE trip_id = :tripId")
    suspend fun getLastTs(tripId: String): Long?

    @Query("SELECT * FROM custody_events WHERE trip_id = :tripId ORDER BY seq DESC LIMIT 1")
    suspend fun getLast(tripId: String): CustodyEventEntity?
}

@Dao
abstract class ExcursionDao {
    @Insert
    abstract suspend fun insert(excursion: ExcursionEntity)

    @Update
    abstract suspend fun update(excursion: ExcursionEntity)

    @Query("SELECT * FROM excursions WHERE trip_id = :tripId AND start_utc_ms = :startUtcMs")
    abstract suspend fun find(tripId: String, startUtcMs: Long): ExcursionEntity?

    @Query("SELECT * FROM excursions WHERE trip_id = :tripId ORDER BY start_utc_ms")
    abstract suspend fun getAll(tripId: String): List<ExcursionEntity>

    /** An excursion is identified by (trip, start); an existing row keeps its id. */
    @Transaction
    open suspend fun upsert(excursion: ExcursionEntity) {
        val existing = find(excursion.tripId, excursion.startUtcMs)
        if (existing == null) insert(excursion) else update(excursion.copy(id = existing.id))
    }
}

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(sensor: SensorEntity)

    @Query("SELECT * FROM sensors WHERE mac_hash = :macHash")
    suspend fun findByHash(macHash: String): SensorEntity?

    @Query("SELECT * FROM sensors WHERE id = :id")
    suspend fun get(id: String): SensorEntity?

    /** The paired sensor is the most recently paired (or re-paired) one. */
    @Query("SELECT * FROM sensors ORDER BY created_at_utc_ms DESC LIMIT 1")
    suspend fun latest(): SensorEntity?

    @Query("UPDATE sensors SET created_at_utc_ms = :nowUtcMs, model = COALESCE(:model, model) WHERE id = :id")
    suspend fun repair(id: String, nowUtcMs: Long, model: String?)
}

@Dao
interface ThresholdDao {
    @Query("SELECT * FROM threshold_profiles WHERE id = :id")
    suspend fun get(id: String): ThresholdProfileEntity?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun put(profile: ThresholdProfileEntity)
}

@Dao
interface ReportDao {
    @Insert
    suspend fun insert(report: ReportEntity)

    @Query("SELECT * FROM reports WHERE trip_id = :tripId ORDER BY created_at_utc_ms DESC")
    suspend fun getAll(tripId: String): List<ReportEntity>
}
