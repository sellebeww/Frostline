// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All ids are UUID strings (sync-ready). Timestamps are UTC milliseconds.

/** A vaccine carrier (coolbox). */
@Entity(tableName = "carriers")
data class CarrierEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "capacity_liters") val capacityLiters: Double?,
    val notes: String?,
)

/** [address] is the BLE address. The whole database is encrypted; [macHash] is what may appear in UI or exports. */
@Entity(tableName = "sensors", indices = [Index(value = ["mac_hash"], unique = true)])
data class SensorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "mac_hash") val macHash: String,
    val model: String?,
    val address: String,
    @ColumnInfo(name = "created_at_utc_ms") val createdAtUtcMs: Long,
)

@Entity(tableName = "threshold_profiles")
data class ThresholdProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "min_c") val minC: Double,
    @ColumnInfo(name = "max_c") val maxC: Double,
    @ColumnInfo(name = "freeze_c") val freezeC: Double,
)

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(ThresholdProfileEntity::class, ["id"], ["threshold_profile_id"]),
        ForeignKey(CarrierEntity::class, ["id"], ["carrier_id"]),
        ForeignKey(SensorEntity::class, ["id"], ["sensor_id"]),
    ],
    indices = [Index("status"), Index("threshold_profile_id"), Index("carrier_id"), Index("sensor_id")],
)
data class TripEntity(
    @PrimaryKey val id: String,
    val status: String,
    @ColumnInfo(name = "threshold_profile_id") val thresholdProfileId: String,
    @ColumnInfo(name = "carrier_id") val carrierId: String?,
    @ColumnInfo(name = "sensor_id") val sensorId: String?,
    @ColumnInfo(name = "operator_id") val operatorId: String,
    @ColumnInfo(name = "started_utc_ms") val startedUtcMs: Long,
    @ColumnInfo(name = "ended_utc_ms") val endedUtcMs: Long?,
    @ColumnInfo(name = "log_interval_sec") val logIntervalSec: Int,
)

@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["id"], ["trip_id"]),
        ForeignKey(SensorEntity::class, ["id"], ["sensor_id"]),
    ],
    indices = [Index(value = ["trip_id", "ts_utc_ms"]), Index("sensor_id")],
)
data class ReadingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "sensor_id") val sensorId: String?,
    @ColumnInfo(name = "ts_utc_ms") val tsUtcMs: Long,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "temp_c") val tempC: Double,
    val humidity: Double?,
)

/** Append-only: guarded by SQLite triggers (see [DatabaseTriggers]) and by the absence of update and delete in the DAO. */
@Entity(
    tableName = "custody_events",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["trip_id"])],
    indices = [Index(value = ["trip_id", "seq"], unique = true)],
)
data class CustodyEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val seq: Int,
    val type: String,
    @ColumnInfo(name = "nfc_tag_id") val nfcTagId: String?,
    @ColumnInfo(name = "nfc_tag_label") val nfcTagLabel: String?,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Double?,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "ts_utc_ms") val tsUtcMs: Long,
    @ColumnInfo(name = "temp_centi") val tempCenti: Int?,
    @ColumnInfo(name = "excursion_open") val excursionOpen: Boolean,
    @ColumnInfo(name = "readings_digest") val readingsDigest: String,
    @ColumnInfo(name = "prev_hash") val prevHash: String,
    val hash: String,
    val signature: String,
    /** Unsigned diagnostic note (hashed tag UID, how the location was obtained). Not part of the hash chain. */
    val diagnostic: String? = null,
)

@Entity(
    tableName = "excursions",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["trip_id"])],
    indices = [Index(value = ["trip_id", "start_utc_ms"], unique = true)],
)
data class ExcursionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val type: String,
    @ColumnInfo(name = "start_utc_ms") val startUtcMs: Long,
    @ColumnInfo(name = "end_utc_ms") val endUtcMs: Long?,
    @ColumnInfo(name = "peak_c") val peakC: Double,
)

@Entity(
    tableName = "reports",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["trip_id"])],
    indices = [Index("trip_id")],
)
data class ReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "pdf_sha256") val pdfSha256: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "created_at_utc_ms") val createdAtUtcMs: Long,
    val path: String,
)
