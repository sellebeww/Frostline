// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.Sensor
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()

fun Reading.toEntity(tripId: String) =
    ReadingEntity(newId(), tripId, null, tsUtcMs, elapsedMs, tempC, humidityPct)

fun ReadingEntity.toDomain() = Reading(tsUtcMs, elapsedMs, tempC, humidity)

fun ThresholdProfile.toEntity(id: String, name: String) = ThresholdProfileEntity(id, name, minC, maxC, freezeC)

fun Trip.toEntity(thresholdProfileId: String) = TripEntity(
    id, status.name, thresholdProfileId, carrierId, sensorId, operatorId, startedUtcMs, endedUtcMs, logIntervalSec,
)

fun TripEntity.toDomain(profile: ThresholdProfileEntity) = Trip(
    id, carrierId, operatorId, startedUtcMs, endedUtcMs, TripStatus.valueOf(status), logIntervalSec,
    ThresholdProfile(profile.minC, profile.maxC, profile.freezeC), sensorId,
)

fun Excursion.toEntity() = ExcursionEntity(newId(), tripId, type.name, startUtcMs, endUtcMs, peakC)

fun ExcursionEntity.toDomain() = Excursion(tripId, ExcursionType.valueOf(type), startUtcMs, endUtcMs, peakC)

fun CustodyEvent.toEntity(diagnostic: String?) = CustodyEventEntity(
    newId(), tripId, seq, type.name, tagUid, tagLabel, lat, lon, accuracyM, userId, tsUtcMs, tempCenti, excursionOpen,
    readingsDigest, prevHash, hash, signatureHex, diagnostic,
)

fun CustodyEventEntity.toDomain() = CustodyEvent(
    tripId, seq, CustodyType.valueOf(type), nfcTagId, nfcTagLabel, tsUtcMs, lat, lon, accuracy, userId, tempCenti,
    excursionOpen, readingsDigest, prevHash, hash, signature,
)

fun SensorEntity.toDomain() = Sensor(id, macHash, model, address)
