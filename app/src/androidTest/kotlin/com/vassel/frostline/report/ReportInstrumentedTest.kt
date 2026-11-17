// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.db.RoomCarrierRepository
import com.vassel.frostline.data.db.RoomCustodyRepository
import com.vassel.frostline.data.db.RoomExcursionRepository
import com.vassel.frostline.data.db.RoomReadingRepository
import com.vassel.frostline.data.db.RoomReportRepository
import com.vassel.frostline.data.db.RoomSensorRepository
import com.vassel.frostline.data.db.RoomTransactionRunner
import com.vassel.frostline.data.db.RoomTripRepository
import com.vassel.frostline.data.report.PdfReportRenderer
import com.vassel.frostline.data.report.ReportGenerator
import com.vassel.frostline.data.security.KeystoreSigner
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.GeoLocation
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.LogReading
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.StartTrip
import java.io.File
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real PdfDocument and the real Android Keystore key, on an emulator or device. The files stay in the app files directory
 * (`reports/<trip>/`) so they can be pulled and checked with tools/verify_report.py.
 */
@RunWith(AndroidJUnit4::class)
class ReportInstrumentedTest {
    private class FixedClock(var now: Long) : Clock {
        override fun nowUtcMs() = now
        override fun elapsedRealtimeMs() = now
    }

    private object Here : LocationProvider {
        override suspend fun currentLocation(): LocationResult = LocationResult.Fix(GeoLocation(-6.2088, 106.8456, 8.0, 0L))
    }

    @Test
    fun realPdfHasMultiplePagesAndTheManifestBindsItsHash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "reports").deleteRecursively()
        val db = FrostlineDatabase.create(context, null, null)
        val clock = FixedClock(1_800_000_000_000L)
        val signer = KeystoreSigner()
        val trips = RoomTripRepository(db); val readings = RoomReadingRepository(db); val excursions = RoomExcursionRepository(db)
        val custody = RoomCustodyRepository(db); val tx = RoomTransactionRunner(db)
        val record = RecordCustodyEvent(trips, readings, custody, excursions, Here, signer, tx, clock)
        try {
            val trip = StartTrip(trips, clock)(null, "operator-uuid-0001")
            val log = LogReading(trips, readings, excursions, clock)
            (0 until 90).forEach { i ->
                clock.now = 1_800_000_000_000L + i * 60_000L
                log(trip.id, Reading(clock.now, i * 60_000L, if (i in 30..45) 9.5 else 5.0 + (i % 4) * 0.2))
            }
            record(trip.id, CustodyType.PICKUP, "tag-0", "Gudang Dinkes", "operator-uuid-0001")
            (1..24).forEach { record(trip.id, CustodyType.HANDOFF, "tag-$it", "Titik $it", "operator-uuid-0001") }
            CompleteTrip(record, trips, excursions, tx)(trip.id, "tag-z", "Posyandu", "operator-uuid-0001")

            val generator = ReportGenerator(
                context, trips, readings, excursions, custody, RoomCarrierRepository(db), RoomSensorRepository(db),
                RoomReportRepository(db), signer, signer, clock, PdfReportRenderer(context),
            )
            val r = generator.generate(trip.id)
            val pdf = r.pdf.readBytes()
            assertTrue(String(pdf, 0, 5, Charsets.ISO_8859_1) == "%PDF-", "not a PDF")
            val pages = Regex("/Type\\s*/Page[^s]").findAll(String(pdf, Charsets.ISO_8859_1)).count()
            assertTrue(pages >= 2, "expected a multi-page report, found $pages page objects")
            assertTrue(r.manifest.readText().contains("\"size_bytes\":${pdf.size}"))
        } finally {
            db.close()
        }
    }
}
