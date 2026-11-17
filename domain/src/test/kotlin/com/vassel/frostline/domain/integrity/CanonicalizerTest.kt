// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.r
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CanonicalizerTest {
    private val prevA = "a".repeat(64)

    @Test
    fun readingsDigestGolden() {
        val readings = listOf(r(1767225600, 5.23), r(1767225540, 5.0)) // unsorted on purpose
        assertEquals("fb40f2f51fd4b2b035ba3ce5903b07e31e747f6bb72b827d42fd7125e8578df2", Canonicalizer.readingsDigest(readings))
        assertEquals("1767225540000|500\n", Canonicalizer.readingLine(r(1767225540, 5.0)))
    }

    @Test
    fun emptyReadingsDigestIsSha256OfEmpty() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Canonicalizer.readingsDigest(emptyList()))
    }

    @Test
    fun tempCentiRoundsHalfUpAndHandlesNegatives() {
        assertEquals(523, r(0, 5.23).tempCenti)
        assertEquals(-50, r(0, -0.5).tempCenti)
        assertEquals(1, r(0, 0.005).tempCenti)
    }

    @Test
    fun fullEventGolden() {
        val f = fields(
            CustodyType.HANDOFF, 1767225600000L, tag = "04a1b2c3d4e5f6", label = "Posyandu Melati",
            lat = -6.2, lon = 106.816666, acc = 12.5, temp = 523, excursionOpen = true,
            digest = "fb40f2f51fd4b2b035ba3ce5903b07e31e747f6bb72b827d42fd7125e8578df2",
        )
        val expected = listOf(
            "v=1", "trip=trip-1", "seq=2", "type=HANDOFF", "tag=04a1b2c3d4e5f6", "tag_label=Posyandu Melati",
            "ts=1767225600000", "lat=-6.2000000", "lon=106.8166660", "acc=12.5", "user=petugas-01",
            "temp_centi=523", "excursion_open=1",
            "readings_digest=fb40f2f51fd4b2b035ba3ce5903b07e31e747f6bb72b827d42fd7125e8578df2", "prev=$prevA",
        ).joinToString("\n")
        val canonical = Canonicalizer.eventCanonical(f.copy(tripId = "trip-0001"), 2, prevA)
        assertEquals(expected.replace("trip=trip-1", "trip=trip-0001"), canonical)
        assertEquals("219174d446ad905389ccf1b48fe1f86693e85cc3fe3130d5212af7d3207b7636", Hex.sha256(canonical.toByteArray()))
    }

    @Test
    fun absentValuesAreDashAndGenesisPrevIsZeros() {
        val f = fields(CustodyType.PICKUP, 1767225000000L, tag = null, label = null, lat = null, lon = null, acc = null, temp = null)
            .copy(tripId = "trip-0001")
        val canonical = Canonicalizer.eventCanonical(f, 1, HashChain.GENESIS_PREV)
        assertEquals(
            listOf(
                "v=1", "trip=trip-0001", "seq=1", "type=PICKUP", "tag=-", "tag_label=-", "ts=1767225000000", "lat=-",
                "lon=-", "acc=-", "user=petugas-01", "temp_centi=-", "excursion_open=0",
                "readings_digest=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "prev=" + "0".repeat(64),
            ).joinToString("\n"),
            canonical,
        )
        assertEquals("97fce49d4e123ff8fdc0e18aed43650159ae62f40c87ee195981711d28c17ea0", Hex.sha256(canonical.toByteArray()))
        assertEquals("0".repeat(64), HashChain.GENESIS_PREV)
    }

    @Test
    fun separatorsInValuesAreEscapedSoLinesCannotBeInjected() {
        val f = fields(CustodyType.PICKUP, 1L, tag = "-", label = "a%b\rc\nprev=zz", trip = "t\n1")
        val canonical = Canonicalizer.eventCanonical(f, 1, HashChain.GENESIS_PREV)
        assertEquals(14, canonical.count { it == '\n' })
        assertEquals(true, canonical.contains("trip=t%0A1"))
        assertEquals(true, canonical.contains("tag=%2D"))
        assertEquals(true, canonical.contains("tag_label=a%25b%0Dc%0Aprev=zz"))
    }

    @Test
    fun coordinatesUseDotAndSevenDecimalsRegardlessOfLocale() {
        val old = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("id-ID"))
        try {
            val c = Canonicalizer.eventCanonical(fields(CustodyType.PICKUP, 1L, lat = -0.00000001, lon = 1.5), 1, HashChain.GENESIS_PREV)
            assertEquals(true, c.contains("lat=0.0000000"))
            assertEquals(true, c.contains("lon=1.5000000"))
            assertFalse(c.contains(","))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun diagnosticListsTheUidHashAndHowTheLocationWasObtained() {
        val fix = com.vassel.frostline.domain.port.GeoLocation(1.0, 2.0, 3.0, 0L)
        assertEquals("uid=abc;loc=ok", CustodyDiagnostic.of("abc", com.vassel.frostline.domain.port.LocationResult.Fix(fix)))
        assertEquals("loc=approximate", CustodyDiagnostic.of(null, com.vassel.frostline.domain.port.LocationResult.Fix(fix, true)))
        assertEquals("loc=none:PERMISSION_DENIED", CustodyDiagnostic.of(null, com.vassel.frostline.domain.port.LocationResult.None(com.vassel.frostline.domain.port.LocationFailure.PERMISSION_DENIED)))
    }
}
