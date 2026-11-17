// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.engine.TripStatsCalculator
import com.vassel.frostline.domain.engine.Weighting
import com.vassel.frostline.domain.integrity.Canonicalizer
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.ChainVerifier
import com.vassel.frostline.domain.integrity.ClockJumpDetector
import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.model.Carrier
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.usecase.ReportData
import java.security.MessageDigest
import java.security.PublicKey

/** Deterministic "signature" (SHA-256 of the data) so golden values stay stable. Never valid as ECDSA. */
object StubSigner : Signer {
    override fun sign(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}

object ReportSample {
    const val TRIP_ID = "trip-report-0001"
    const val T0 = 1_767_225_600_000L

    /** 12 readings a minute apart. Minutes 4 to 7 are 9.0 to 9.3 (one HEAT excursion), the rest 5.0 to 5.2. */
    val readings: List<Reading> = (0 until 12).map { i ->
        val temp = if (i in 4..7) 9.0 + (i - 4) * 0.1 else 5.0 + (i % 3) * 0.1
        Reading(T0 + i * 60_000L, 5_000_000L + i * 60_000L, temp, if (i % 2 == 0) 61.5 else null)
    }

    val excursions = listOf(Excursion(TRIP_ID, ExcursionType.HEAT, T0 + 4 * 60_000L, T0 + 8 * 60_000L, 9.3))

    val trip = Trip(
        TRIP_ID, "carrier-1", "petugas-01", T0, T0 + 11 * 60_000L, TripStatus.COMPLETED, 60, ThresholdProfile(), "sensor-1",
    )

    private fun fieldsAt(type: CustodyType, minute: Int, tag: String, label: String, lat: Double?, lon: Double?) = run {
        val ts = T0 + minute * 60_000L
        val upTo = readings.filter { it.tsUtcMs <= ts }
        CustodyFields(
            TRIP_ID, type, tag, label, ts, lat, lon, if (lat == null) null else 7.25, "petugas-01",
            upTo.last().tempCenti, minute in 5..8, Canonicalizer.readingsDigest(upTo),
        )
    }

    fun custody(signer: Signer): List<CustodyEvent> {
        val specs = listOf(
            fieldsAt(CustodyType.PICKUP, 0, "tag-aaaa", "Gudang Dinkes", -6.2088, 106.8456),
            fieldsAt(CustodyType.HANDOFF, 6, "tag-bbbb", "Puskesmas \"Sehat\" 100%", null, null),
            fieldsAt(CustodyType.DELIVERY, 11, "tag-cccc", "Posyandu Melati", -6.22, 106.86),
        )
        val out = mutableListOf<CustodyEvent>()
        specs.forEach { out += HashChain.appendEvent(out.lastOrNull(), it, signer) }
        return out
    }

    fun data(
        signer: Signer,
        publicKey: PublicKey? = null,
        trip: Trip = ReportSample.trip,
        sensorMacHash: String? = "0123456789ab",
    ): ReportData {
        val events = custody(signer)
        return ReportData(
            trip = trip,
            carrier = Carrier("carrier-1", "Kotak Puskesmas \"A\"", 2.5, null),
            sensorMacHash = sensorMacHash,
            sensorModel = "ESP32-ESS",
            readings = readings,
            excursions = excursions,
            custody = events,
            stats = TripStatsCalculator.compute(readings, excursions, trip.logIntervalSec, trip.endedUtcMs ?: 0L),
            gaps = Weighting.analyze(readings, trip.logIntervalSec).gaps,
            clockJumps = ClockJumpDetector.detect(readings),
            chainVerification = if (publicKey == null) ChainVerification.Valid else ChainVerifier.verify(events, publicKey),
            readingsDigest = Canonicalizer.readingsDigest(readings),
        )
    }

    fun fingerprint(publicKeyX509: ByteArray) = Hex.sha256(publicKeyX509)
}
