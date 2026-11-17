// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.Reading
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** CSV exports. Numbers always use a dot and two decimals, independent of the device locale. Lines end with a newline. */
object CsvExport {
    private val millisIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** Temperature in 0.01 degC as text, exact (no floating point). */
    fun centi(centi: Int): String {
        val sign = if (centi < 0) "-" else ""
        val a = abs(centi.toLong())
        return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }

    /** Capture order. Timestamps are millisecond ISO-8601 UTC. */
    fun readings(tripId: String, readings: List<Reading>): String = buildString {
        append("ts_utc_iso,temp_c,humidity,trip_id\n")
        for (r in readings) {
            append(timestamp(r.tsUtcMs)).append(',').append(centi(r.tempCenti)).append(',')
            r.humidityPct?.let { append(String.format(Locale.ROOT, "%.2f", it)) }
            append(',').append(field(tripId)).append('\n')
        }
    }

    fun excursions(excursions: List<Excursion>): String = buildString {
        append("trip_id,type,start_utc_iso,end_utc_iso,duration_s,peak_c\n")
        for (e in excursions) {
            val end = e.endUtcMs
            append(field(e.tripId)).append(',').append(e.type.name).append(',').append(timestamp(e.startUtcMs)).append(',')
            if (end != null) append(timestamp(end))
            append(',')
            if (end != null) append((end - e.startUtcMs) / 1000)
            append(',').append(centi(Math.round(e.peakC * 100.0).toInt())).append('\n')
        }
    }

    private fun timestamp(ms: Long): String = millisIso.format(Instant.ofEpochMilli(ms))

    private fun field(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
