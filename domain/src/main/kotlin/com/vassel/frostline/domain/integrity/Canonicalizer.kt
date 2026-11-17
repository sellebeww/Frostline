// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.Reading
import java.math.BigDecimal
import java.math.RoundingMode

/** Canonical text forms that are hashed and signed (SPEC.md 5.3). Changing them breaks every existing chain. */
object Canonicalizer {
    const val VERSION = 1
    private const val ABSENT = "-"

    fun readingLine(r: Reading): String = "${r.tsUtcMs}|${r.tempCenti}\n"

    /** SHA-256 over the reading lines sorted by timestamp. */
    fun readingsDigest(readings: List<Reading>): String {
        val sb = StringBuilder()
        readings.sortedWith(compareBy({ it.tsUtcMs }, { it.elapsedMs })).forEach { sb.append(readingLine(it)) }
        return Hex.sha256(sb.toString().toByteArray(Charsets.UTF_8))
    }

    /** Fixed key order, lines joined by a newline without a trailing newline. */
    fun eventCanonical(f: CustodyFields, seq: Int, prevHash: String): String = listOf(
        "v" to VERSION.toString(),
        "trip" to text(f.tripId),
        "seq" to seq.toString(),
        "type" to f.type.name,
        "tag" to (f.tagUid?.let(::text) ?: ABSENT),
        "tag_label" to (f.tagLabel?.let(::text) ?: ABSENT),
        "ts" to f.tsUtcMs.toString(),
        "lat" to (f.lat?.let { decimal(it, 7) } ?: ABSENT),
        "lon" to (f.lon?.let { decimal(it, 7) } ?: ABSENT),
        "acc" to (f.accuracyM?.let { decimal(it, 1) } ?: ABSENT),
        "user" to text(f.userId),
        "temp_centi" to (f.tempCenti?.toString() ?: ABSENT),
        "excursion_open" to if (f.excursionOpen) "1" else "0",
        "readings_digest" to f.readingsDigest,
        "prev" to prevHash,
    ).joinToString("\n") { (k, v) -> "$k=$v" }

    /** Percent-escapes characters that could forge lines, and a lone "-" that would read as absent. */
    private fun text(v: String): String {
        if (v == ABSENT) return "%2D"
        return v.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D")
    }

    private fun decimal(v: Double, places: Int): String =
        BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).toPlainString()
}
