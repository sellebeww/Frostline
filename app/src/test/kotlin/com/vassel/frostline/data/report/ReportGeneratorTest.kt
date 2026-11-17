// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.report

import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.db.JcaSigner
import com.vassel.frostline.data.db.RoomCarrierRepository
import com.vassel.frostline.data.db.RoomCustodyRepository
import com.vassel.frostline.data.db.RoomExcursionRepository
import com.vassel.frostline.data.db.RoomReadingRepository
import com.vassel.frostline.data.db.RoomReportRepository
import com.vassel.frostline.data.db.RoomSensorRepository
import com.vassel.frostline.data.db.RoomTransactionRunner
import com.vassel.frostline.data.db.RoomTripRepository
import com.vassel.frostline.data.db.TestClock
import com.vassel.frostline.data.db.TestLocation
import com.vassel.frostline.data.db.inMemoryDb
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.port.PublicKeySource
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.LogReading
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.StartTrip
import com.vassel.frostline.domain.usecase.ReportData
import java.io.File
import java.io.OutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Report generation on real Room repositories with a simulated trip (readings with a HEAT excursion, PICKUP, HANDOFF,
 * DELIVERY). Robolectric has no native PdfDocument (even in native graphics mode), so a fake [ReportPdfWriter] stands in
 * here and the real rendering is covered by the instrumented ReportInstrumentedTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportGeneratorTest {
    private object FakePdf : ReportPdfWriter {
        /** Deterministic stand-in: carries the hashes the real PDF prints, so a changed content shows in the bytes. */
        override fun render(data: ReportData, integrity: ReportIntegrity, out: OutputStream) =
            out.write("%PDF-fake ${data.trip.id} ${integrity.contentHash} ${integrity.keyFingerprint}\n".toByteArray())
    }

    private lateinit var db: FrostlineDatabase
    private val signer = JcaSigner()
    private val clock = TestClock()
    private lateinit var trips: RoomTripRepository
    private lateinit var readings: RoomReadingRepository
    private lateinit var excursions: RoomExcursionRepository
    private lateinit var custody: RoomCustodyRepository
    private lateinit var reports: RoomReportRepository
    private lateinit var generator: ReportGenerator
    private lateinit var record: RecordCustodyEvent

    @Before
    fun setUp() {
        db = inMemoryDb()
        trips = RoomTripRepository(db); readings = RoomReadingRepository(db); excursions = RoomExcursionRepository(db)
        custody = RoomCustodyRepository(db); reports = RoomReportRepository(db)
        val tx = RoomTransactionRunner(db)
        record = RecordCustodyEvent(trips, readings, custody, excursions, TestLocation(), signer, tx, clock)
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "reports").deleteRecursively()
        generator = ReportGenerator(
            context, trips, readings, excursions, custody, RoomCarrierRepository(db), RoomSensorRepository(db), reports,
            signer, object : PublicKeySource { override fun publicKey() = signer.publicKey }, clock, FakePdf,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun simulatedTrip(complete: Boolean): String {
        val trip = StartTrip(trips, clock)(null, "op")
        val log = LogReading(trips, readings, excursions, clock)
        (0 until 12).forEach { i ->
            val t = 1_000L + i * 60_000L
            log(trip.id, Reading(t, t, if (i in 4..7) 9.0 + (i - 4) * 0.1 else 5.0 + (i % 3) * 0.1))
        }
        clock.now = 5_000
        record(trip.id, CustodyType.PICKUP, "tag1", "Gudang", "op")
        clock.now = 400_000
        record(trip.id, CustodyType.HANDOFF, "tag2", "Puskesmas", "op")
        if (complete) {
            clock.now = 700_000
            CompleteTrip(record, trips, excursions, RoomTransactionRunner(db))(trip.id, "tag3", "Posyandu", "op")
        }
        return trip.id
    }

    @Test
    fun activeTripIsRejectedAndNothingIsWritten() = runTest {
        val id = simulatedTrip(complete = false)
        assertFailsWith<TripNotCompletedException> { generator.generate(id) }
        assertTrue(reports.getAll(id).isEmpty())
        assertTrue(!File(RuntimeEnvironment.getApplication().filesDir, "reports/$id").exists())
    }

    @Test
    fun completedTripProducesFourFilesAndTheManifestBindsThePdfHash() = runTest {
        val id = simulatedTrip(complete = true)
        val r = generator.generate(id)
        r.files.forEach { assertTrue(it.length() > 0, "${it.name} is empty") }
        val manifest = r.manifest.readText()
        assertContains(manifest, "\"sha256\":\"${Hex.sha256(r.pdf.readBytes())}\"")
        assertContains(manifest, "\"size_bytes\":${r.pdf.length()}")
        assertContains(manifest, "\"content_hash\":\"${r.contentHash}\"")
        assertEquals(13, r.readingsCsv.readLines().size, "header plus 12 readings")
        val rows = reports.getAll(id)
        assertEquals(1, rows.size)
        assertEquals(Hex.sha256(r.pdf.readBytes()), rows[0].pdfSha256)
        assertEquals(Hex.sha256(r.manifest.readBytes()), rows[0].manifestSha256)
    }

    @Test
    fun regeneratingKeepsTheCanonicalContentIdenticalAndAddsATraceRow() = runTest {
        val id = simulatedTrip(complete = true)
        clock.now = 800_000
        val first = generator.generate(id)
        val firstContent = contentOf(first.manifest.readText())
        clock.now = 900_000
        val second = generator.generate(id)
        assertEquals(first.contentHash, second.contentHash)
        assertEquals(firstContent, contentOf(second.manifest.readText()), "content must be byte-identical")
        assertEquals(2, reports.getAll(id).size)
    }

    /** The substring of the canonical manifest between `"content":` and the next top-level key `"content_hash"`. */
    private fun contentOf(manifest: String): String =
        manifest.substringAfter("\"content\":").substringBefore(",\"content_hash\":")
}
