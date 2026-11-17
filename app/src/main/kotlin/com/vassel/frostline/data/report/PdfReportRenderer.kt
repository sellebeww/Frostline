// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.vassel.frostline.R
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.model.Excursion
import com.vassel.frostline.domain.model.ExcursionType
import com.vassel.frostline.domain.timeline.TripTimeline
import com.vassel.frostline.domain.usecase.ReportData
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Values the PDF shows that come from the manifest, so both documents quote the same hashes. */
data class ReportIntegrity(val contentHash: String, val keyFingerprint: String)

/** Writes the PDF of a report. A seam so that JVM tests (Robolectric has no native PdfDocument) can use a fake. */
interface ReportPdfWriter {
    fun render(data: ReportData, integrity: ReportIntegrity, out: OutputStream)
}

/**
 * Draws the report with [PdfDocument] (A4, 595 x 842 pt, as many pages as needed). It prints no generation time and
 * nothing random, so the same stored data renders the same text. No claim is made that the PDF is signed: it is bound
 * by hash in the signed manifest.
 */
class PdfReportRenderer @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) : ReportPdfWriter {
    override fun render(data: ReportData, integrity: ReportIntegrity, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Layout(doc, context, data.trip.id).apply {
                draw(data, integrity)
                finish()
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Layout(private val doc: PdfDocument, private val context: Context, private val tripId: String) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 40f
        private val contentW = pageW - 2 * margin
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.BLACK }
        private val small = Paint(body).apply { textSize = 8f; color = Color.DKGRAY }
        private val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        private val h1 = Paint(body).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        private val h2 = Paint(body).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        private val mono = Paint(body).apply { typeface = Typeface.MONOSPACE; textSize = 8f }
        private val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)

        private fun s(id: Int, vararg args: Any) = context.getString(id, *args)
        private fun utc(ms: Long) = dateFmt.format(Instant.ofEpochMilli(ms))
        private fun temp(c: Double) = String.format(Locale.ROOT, "%.1f", c)
        private fun dur(ms: Long): String {
            val sec = ms / 1000
            return String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        }

        private fun newPage() {
            if (pageNo > 0) closePage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page.canvas
            y = margin
        }

        private fun closePage() {
            canvas.drawLine(margin, pageH - 30f, pageW - margin, pageH - 30f, rule)
            canvas.drawText(s(R.string.report_page, pageNo, tripId), margin, pageH - 18f, small)
            doc.finishPage(page)
        }

        fun finish() = closePage()

        private fun ensure(h: Float) { if (y + h > pageH - 42f) newPage() }

        private fun line(text: String, paint: Paint = body, indent: Float = 0f, gap: Float = 3f) {
            val lines = wrap(text, paint, contentW - indent)
            for (l in lines) {
                ensure(paint.textSize + gap)
                y += paint.textSize
                canvas.drawText(l, margin + indent, y, paint)
                y += gap
            }
        }

        private fun section(title: String) {
            ensure(34f)
            y += 8f
            y += h2.textSize
            canvas.drawText(title, margin, y, h2)
            y += 4f
            canvas.drawLine(margin, y, pageW - margin, y, rule)
            y += 6f
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            for (para in text.split("\n")) {
                var rest = para
                if (rest.isEmpty()) { out += ""; continue }
                while (rest.isNotEmpty()) {
                    val n = paint.breakText(rest, true, width, null)
                    if (n >= rest.length) { out += rest; break }
                    val space = rest.lastIndexOf(' ', n - 1).takeIf { it > 0 } ?: n
                    out += rest.substring(0, space).trimEnd()
                    rest = rest.substring(space).trimStart()
                }
            }
            return out
        }

        private fun fit(text: String, paint: Paint, width: Float): String {
            val n = paint.breakText(text, true, width, null)
            return if (n >= text.length) text else text.substring(0, maxOf(0, n - 3)) + "..."
        }

        private fun row(cells: List<String>, xs: List<Float>, paint: Paint = body) {
            ensure(paint.textSize + 5f)
            y += paint.textSize
            cells.forEachIndexed { i, c ->
                val end = if (i + 1 < xs.size) xs[i + 1] - 4f else contentW
                canvas.drawText(fit(c, paint, end - xs[i]), margin + xs[i], y, paint)
            }
            y += 5f
        }

        private fun fields(vararg pairs: Pair<String, String>) {
            for ((k, v) in pairs) {
                ensure(14f)
                y += body.textSize
                canvas.drawText(k, margin, y, bold)
                val lines = wrap(v, body, contentW - 120f)
                lines.forEachIndexed { i, l ->
                    if (i > 0) { ensure(14f); y += body.textSize + 3f }
                    canvas.drawText(l, margin + 120f, y, body)
                }
                y += 4f
            }
        }

        fun draw(d: ReportData, integrity: ReportIntegrity) {
            newPage()
            y += h1.textSize
            canvas.drawText(s(R.string.report_pdf_title), margin, y, h1)
            y += 6f
            line(s(R.string.report_pdf_subtitle), small)

            val none = s(R.string.report_none)
            section(s(R.string.report_sec_trip))
            fields(
                s(R.string.report_trip_id) to d.trip.id,
                s(R.string.report_operator) to d.trip.operatorId,
                s(R.string.report_carrier) to (d.carrier?.name ?: none),
                s(R.string.report_sensor) to (d.sensorMacHash?.let { h -> h + (d.sensorModel?.let { " ($it)" } ?: "") } ?: none),
                s(R.string.report_started) to utc(d.trip.startedUtcMs),
                s(R.string.report_ended) to (d.trip.endedUtcMs?.let(::utc) ?: none),
                s(R.string.report_interval) to s(R.string.report_interval_value, d.trip.logIntervalSec),
            )

            section(s(R.string.report_sec_thresholds))
            val t = d.trip.thresholds
            line(s(R.string.report_thresholds_value, temp(t.minC), temp(t.maxC), temp(t.freezeC)))
            line(s(R.string.report_thresholds_note), small)

            val st = d.stats
            section(s(R.string.report_sec_stats))
            line(s(R.string.report_stat_readings, st.readingCount))
            line(s(R.string.report_stat_range, st.minC?.let(::temp) ?: "-", st.maxC?.let(::temp) ?: "-", st.meanC?.let(::temp) ?: "-"))
            line(s(R.string.report_stat_mkt, st.mktC?.let(::temp) ?: "-"))
            line(s(R.string.report_stat_out, dur(st.outOfRangeMs)))
            line(s(R.string.report_stat_excursions, st.excursionCount, dur(st.longestExcursionMs)))
            line(s(R.string.report_stat_gaps, st.gapCount, dur(st.gapTotalMs)))

            section(s(R.string.report_sec_chart))
            chart(d)
            line(s(R.string.report_chart_axis), small)

            section(s(R.string.report_sec_excursions))
            if (d.excursions.isEmpty()) line(s(R.string.report_no_excursions)) else excursionTable(d.excursions)

            section(s(R.string.report_sec_custody))
            custodyTable(d)

            section(s(R.string.report_sec_warnings))
            line(s(if (d.chainVerification == ChainVerification.Valid) R.string.report_chain_valid else R.string.report_chain_invalid))
            if (d.clockJumps.isEmpty()) line(s(R.string.report_no_jumps))
            d.clockJumps.forEach { line(s(R.string.report_jump, utc(it.atUtcMs), it.driftMs / 1000)) }

            section(s(R.string.report_sec_integrity))
            line(s(R.string.report_digest, ""), body, gap = 1f)
            line(d.readingsDigest, mono)
            line(s(R.string.report_fingerprint, ""), body, gap = 1f)
            line(integrity.keyFingerprint, mono)
            line(s(R.string.report_content_hash, ""), body, gap = 1f)
            line(integrity.contentHash, mono)
            d.custody.lastOrNull()?.let { e ->
                line(s(R.string.report_last_event, e.hash.take(12), e.signatureHex.take(24) + "..."), mono)
            }
            line(s(R.string.report_integrity_note), small)

            section(s(R.string.report_sec_disclaimer))
            listOf(R.string.report_disclaimer_1, R.string.report_disclaimer_2, R.string.report_disclaimer_3, R.string.report_disclaimer_4)
                .forEach { line(s(it), body, gap = 4f) }
        }

        private fun excursionTable(list: List<Excursion>) {
            val xs = listOf(0f, 90f, 215f, 340f, 425f)
            row(listOf(s(R.string.report_col_type), s(R.string.report_col_start), s(R.string.report_col_end), s(R.string.report_col_duration), s(R.string.report_col_peak)), xs, bold)
            for (e in list) {
                row(
                    listOf(
                        s(when (e.type) { ExcursionType.HEAT -> R.string.type_heat; ExcursionType.COLD -> R.string.type_cold; ExcursionType.FREEZE -> R.string.type_freeze }),
                        utc(e.startUtcMs),
                        e.endUtcMs?.let(::utc) ?: s(R.string.report_open),
                        e.endUtcMs?.let { dur(it - e.startUtcMs) } ?: "-",
                        temp(e.peakC) + " °C",
                    ),
                    xs,
                )
            }
        }

        private fun custodyTable(d: ReportData) {
            val xs = listOf(0f, 22f, 70f, 160f, 255f, 330f, 430f)
            row(
                listOf(s(R.string.report_col_seq), s(R.string.report_col_type), s(R.string.report_col_time), s(R.string.report_col_place), s(R.string.report_col_user), s(R.string.report_col_tag), s(R.string.report_col_hash)),
                xs, bold,
            )
            for (e in d.custody) {
                val lat = e.lat
                val lon = e.lon
                val place = if (lat != null && lon != null) String.format(Locale.ROOT, "%.5f, %.5f", lat, lon) else "-"
                row(listOf(e.seq.toString(), e.type.name, utc(e.tsUtcMs), place, e.userId.take(8), e.tagLabel ?: "-", e.hash.take(12)), xs)
            }
        }

        private fun chart(d: ReportData) {
            val h = 170f
            ensure(h + 24f)
            val left = margin + 34f
            val box = RectF(left, y, pageW - margin, y + h)
            val rs = d.readings
            val tMin = (rs.minOfOrNull { it.tempC } ?: 0.0).coerceAtMost(d.trip.thresholds.minC) - 1.0
            val tMax = (rs.maxOfOrNull { it.tempC } ?: 10.0).coerceAtLeast(d.trip.thresholds.maxC) + 1.0
            val t0 = d.trip.startedUtcMs
            val t1 = maxOf(d.trip.endedUtcMs ?: (rs.lastOrNull()?.tsUtcMs ?: (t0 + 1)), t0 + 1)
            fun px(ts: Long) = box.left + (ts - t0).toFloat() / (t1 - t0).toFloat() * box.width()
            fun py(c: Double) = box.bottom - ((c - tMin) / (tMax - tMin)).toFloat() * box.height()
            val fill = Paint().apply { style = Paint.Style.FILL }

            fill.color = Color.rgb(222, 240, 224)
            canvas.drawRect(box.left, py(d.trip.thresholds.maxC), box.right, py(d.trip.thresholds.minC), fill)
            for (e in d.excursions) {
                fill.color = if (e.type == ExcursionType.HEAT) Color.rgb(250, 214, 208) else Color.rgb(207, 226, 250)
                val a = px(e.startUtcMs).coerceIn(box.left, box.right)
                val b = px(e.endUtcMs ?: t1).coerceIn(box.left, box.right)
                canvas.drawRect(a, box.top, maxOf(b, a + 1f), box.bottom, fill)
            }
            val frame = Paint().apply { style = Paint.Style.STROKE; color = Color.GRAY; strokeWidth = 0.8f }
            canvas.drawRect(box, frame)
            val dash = Paint(frame).apply { color = Color.rgb(46, 125, 50); pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) }
            canvas.drawLine(box.left, py(d.trip.thresholds.maxC), box.right, py(d.trip.thresholds.maxC), dash)
            canvas.drawLine(box.left, py(d.trip.thresholds.minC), box.right, py(d.trip.thresholds.minC), dash)

            val gapMs = TripTimeline.gapThresholdMs(d.trip.logIntervalSec)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(13, 71, 161); strokeWidth = 1.2f }
            val path = Path()
            var prevTs = Long.MIN_VALUE
            for (r in rs) {
                val x = px(r.tsUtcMs)
                val yy = py(r.tempC)
                if (prevTs == Long.MIN_VALUE || r.tsUtcMs - prevTs > gapMs) path.moveTo(x, yy) else path.lineTo(x, yy)
                prevTs = r.tsUtcMs
            }
            canvas.drawPath(path, line)
            if (rs.size == 1) canvas.drawCircle(px(rs[0].tsUtcMs), py(rs[0].tempC), 2f, line.apply { style = Paint.Style.FILL })

            canvas.drawText(temp(tMax), margin, box.top + 8f, small)
            canvas.drawText(temp(tMin), margin, box.bottom, small)
            canvas.drawText(utc(t0), box.left, box.bottom + 11f, small)
            val endText = utc(t1)
            canvas.drawText(endText, box.right - small.measureText(endText), box.bottom + 11f, small)
            y = box.bottom + 18f
        }
    }
}
