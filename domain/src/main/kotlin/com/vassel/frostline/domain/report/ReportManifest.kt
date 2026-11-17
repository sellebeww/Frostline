// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.integrity.Canonicalizer
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.usecase.ReportData
import java.math.BigDecimal
import java.math.RoundingMode

/** The PDF a manifest vouches for. */
data class PdfBinding(val fileName: String, val sha256: String, val sizeBytes: Long)

/** [json] is the exact file text (canonical form). */
data class SignedManifest(val json: String, val contentHash: String, val manifestHash: String, val signatureHex: String)

/**
 * Manifest layout (format_version 1):
 *  - `content`: everything measured (trip, stats, excursions, events, key). Deterministic: regenerating a report from the
 *    same stored data gives byte-identical canonical content, hence the same `content_hash`.
 *  - `pdf`, `generated_at_utc_ms`, `notes`: the part that changes per generation.
 *  - `manifest_hash` = SHA-256 of the canonical JSON of everything except `manifest_hash` and `manifest_signature`;
 *    `manifest_signature` = SHA256withECDSA over the ASCII hex of `manifest_hash`, DER encoded, lowercase hex.
 * The signature is on this manifest, which binds the PDF by hash. The PDF itself carries no embedded signature.
 */
object ReportManifest {
    const val FORMAT = "frostline-manifest"
    const val FORMAT_VERSION = 1

    private const val NOTE_PDF =
        "The PDF is not a PAdES or any embedded-signature document. Authenticity rests on this manifest, which binds the PDF by SHA-256 and is signed with the device key."
    private const val NOTE_SCOPE =
        "tamper-evident, not tamper-proof: a rooted device can rewrite the whole chain before export; the signature proves \"made by this device key\", not a human identity; NFC tags can be cloned. Not a replacement for a WHO PQS certified logger."

    fun content(data: ReportData, publicKeyX509: ByteArray): Map<String, Any?> {
        require(data.trip.status == TripStatus.COMPLETED) { "Reports can only be made for completed trips" }
        val t = data.trip
        val s = data.stats
        return mapOf(
            "trip" to mapOf(
                "id" to t.id,
                "operator" to t.operatorId,
                "carrier" to data.carrier?.let { mapOf("id" to it.id, "name" to it.name) },
                "sensor" to data.sensorMacHash?.let { mapOf("mac_hash" to it, "model" to data.sensorModel) },
                "started_utc_ms" to t.startedUtcMs,
                "ended_utc_ms" to t.endedUtcMs,
                "log_interval_sec" to t.logIntervalSec,
                "thresholds" to mapOf("min_centi" to centi(t.thresholds.minC), "max_centi" to centi(t.thresholds.maxC), "freeze_centi" to centi(t.thresholds.freezeC)),
            ),
            "stats" to mapOf(
                "reading_count" to s.readingCount,
                "min_centi" to s.minC?.let(::centi),
                "max_centi" to s.maxC?.let(::centi),
                "mean_centi" to s.meanC?.let(::centi),
                "mkt_centi" to s.mktC?.let(::centi),
                "out_of_range_ms" to s.outOfRangeMs,
                "excursion_count" to s.excursionCount,
                "longest_excursion_ms" to s.longestExcursionMs,
                "gap_count" to s.gapCount,
                "gap_total_ms" to s.gapTotalMs,
            ),
            "excursions" to data.excursions.map {
                mapOf("type" to it.type.name, "start_utc_ms" to it.startUtcMs, "end_utc_ms" to it.endUtcMs, "peak_centi" to centi(it.peakC))
            },
            "gaps" to data.gaps.map { mapOf("start_utc_ms" to it.startUtcMs, "end_utc_ms" to it.endUtcMs) },
            "clock_jumps" to data.clockJumps.map { mapOf("at_utc_ms" to it.atUtcMs, "drift_ms" to it.driftMs) },
            "events" to data.custody.map(::event),
            "reading_count" to data.readings.size,
            "readings_digest" to data.readingsDigest,
            "chain_check_at_generation" to if (data.chainVerification == ChainVerification.Valid) "valid" else "invalid",
            "public_key_x509_base64" to java.util.Base64.getEncoder().encodeToString(publicKeyX509),
            "key_fingerprint" to Hex.sha256(publicKeyX509),
        )
    }

    fun contentHash(content: Map<String, Any?>): String = Hex.sha256(CanonicalJson.encode(content).toByteArray(Charsets.UTF_8))

    fun finalize(content: Map<String, Any?>, pdf: PdfBinding, generatedAtUtcMs: Long, signer: Signer): SignedManifest {
        val contentHash = contentHash(content)
        val body = mapOf(
            "format" to FORMAT,
            "format_version" to FORMAT_VERSION,
            "content" to content,
            "content_hash" to contentHash,
            "pdf" to mapOf("file_name" to pdf.fileName, "sha256" to pdf.sha256, "size_bytes" to pdf.sizeBytes),
            "generated_at_utc_ms" to generatedAtUtcMs,
            "signature_algorithm" to "SHA256withECDSA",
            "notes" to mapOf("pdf" to NOTE_PDF, "scope" to NOTE_SCOPE),
        )
        val manifestHash = Hex.sha256(CanonicalJson.encode(body).toByteArray(Charsets.UTF_8))
        val signature = Hex.encode(signer.sign(manifestHash.toByteArray(Charsets.UTF_8)))
        val json = CanonicalJson.encode(body + mapOf("manifest_hash" to manifestHash, "manifest_signature" to signature))
        return SignedManifest(json, contentHash, manifestHash, signature)
    }

    /** Degrees Celsius as an integer count of 0.01 degC, rounded half up like [com.vassel.frostline.domain.model.Reading.tempCenti]. */
    private fun centi(c: Double): Long = Math.round(c * 100.0)

    private fun event(e: CustodyEvent): Map<String, Any?> = mapOf(
        "seq" to e.seq,
        "type" to e.type.name,
        "trip" to e.tripId,
        "tag" to e.tagUid,
        "tag_label" to e.tagLabel,
        "ts" to e.tsUtcMs,
        "lat" to e.lat?.let { decimal(it, 7) },
        "lon" to e.lon?.let { decimal(it, 7) },
        "acc" to e.accuracyM?.let { decimal(it, 1) },
        "user" to e.userId,
        "temp_centi" to e.tempCenti,
        "excursion_open" to e.excursionOpen,
        "readings_digest" to e.readingsDigest,
        "prev" to e.prevHash,
        "canonical" to Canonicalizer.eventCanonical(e.toFields(), e.seq, e.prevHash),
        "hash" to e.hash,
        "signature" to e.signatureHex,
    )

    /** Coordinates travel as text so no float formatting differs between languages. Same rounding as the canonical form. */
    private fun decimal(v: Double, places: Int): String = BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).toPlainString()
}
