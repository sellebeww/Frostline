// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.report

import android.content.Context
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.model.TripStatus
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.PublicKeySource
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.ReportRecord
import com.vassel.frostline.domain.port.ReportRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.report.CsvExport
import com.vassel.frostline.domain.report.PdfBinding
import com.vassel.frostline.domain.report.ReportManifest
import com.vassel.frostline.domain.usecase.BuildReportData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The four files of one report, under the app files directory in `reports/<trip id>/`. */
data class GeneratedReport(
    val tripId: String,
    val pdf: File,
    val manifest: File,
    val readingsCsv: File,
    val excursionsCsv: File,
    val contentHash: String,
) {
    val files: List<File> get() = listOf(pdf, manifest, readingsCsv, excursionsCsv)
}

class TripNotCompletedException : IllegalStateException("Reports can only be made for completed trips")

/**
 * Builds the report for a COMPLETED trip: PDF, signed manifest, readings CSV and excursions CSV, then records a trace
 * row in `reports`. Regenerating overwrites the files; the manifest `content` (and `content_hash`) is identical every time,
 * while the PDF hash, `generated_at_utc_ms` and the signature can differ.
 */
class ReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    readings: ReadingRepository,
    excursions: ExcursionRepository,
    custody: CustodyRepository,
    carriers: CarrierRepository,
    sensors: SensorRepository,
    private val reports: ReportRepository,
    private val signer: Signer,
    private val keys: PublicKeySource,
    private val clock: Clock,
    private val renderer: ReportPdfWriter,
) {
    private val buildData = BuildReportData(trips, readings, excursions, custody, carriers, sensors, clock)

    suspend fun generate(tripId: String): GeneratedReport = withContext(Dispatchers.IO) {
        val trip = checkNotNull(trips.get(tripId)) { "Unknown trip" }
        if (trip.status != TripStatus.COMPLETED) throw TripNotCompletedException()
        require(SAFE_ID.matches(tripId)) { "Unsafe trip id" }

        val publicKey = keys.publicKey()
        val data = buildData(tripId, publicKey)
        val content = ReportManifest.content(data, publicKey.encoded)
        val contentHash = ReportManifest.contentHash(content)

        val pdfBytes = ByteArrayOutputStream().also { renderer.render(data, ReportIntegrity(contentHash, Hex.sha256(publicKey.encoded)), it) }.toByteArray()
        val pdfName = "$tripId.pdf"
        val manifest = ReportManifest.finalize(
            content, PdfBinding(pdfName, Hex.sha256(pdfBytes), pdfBytes.size.toLong()), clock.nowUtcMs(), signer,
        )

        val dir = File(context.filesDir, "$REPORT_DIR/$tripId").apply { mkdirs() }
        val result = GeneratedReport(
            tripId,
            pdf = File(dir, pdfName),
            manifest = File(dir, "$tripId.manifest.json"),
            readingsCsv = File(dir, "$tripId.readings.csv"),
            excursionsCsv = File(dir, "$tripId.excursions.csv"),
            contentHash = contentHash,
        )
        val manifestBytes = manifest.json.toByteArray(Charsets.UTF_8)
        writeAtomic(result.pdf, pdfBytes)
        writeAtomic(result.manifest, manifestBytes)
        writeAtomic(result.readingsCsv, CsvExport.readings(tripId, data.readings).toByteArray(Charsets.UTF_8))
        writeAtomic(result.excursionsCsv, CsvExport.excursions(data.excursions).toByteArray(Charsets.UTF_8))

        reports.record(
            ReportRecord(UUID.randomUUID().toString(), tripId, Hex.sha256(pdfBytes), Hex.sha256(manifestBytes), clock.nowUtcMs(), "$REPORT_DIR/$tripId"),
        )
        result
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(target)) { "Could not write ${target.name}" }
    }

    companion object {
        const val REPORT_DIR = "reports"
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
