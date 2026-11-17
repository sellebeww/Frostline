// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.integrity.JcaSigner
import com.vassel.frostline.domain.integrity.newKeyPair
import com.vassel.frostline.domain.model.TripStatus
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReportManifestTest {
    private val pair = newKeyPair()
    private val pub = pair.public.encoded
    private val pdf = PdfBinding("trip.pdf", "ab".repeat(32), 1234)

    @Test
    fun contentIsByteIdenticalWhenRegenerated() {
        val a = ReportManifest.content(ReportSample.data(StubSigner), pub)
        val b = ReportManifest.content(ReportSample.data(StubSigner), pub)
        assertEquals(CanonicalJson.encode(a), CanonicalJson.encode(b))
        assertEquals(ReportManifest.contentHash(a), ReportManifest.contentHash(b))
    }

    @Test
    fun contentChangesWhenAnyMeasuredValueChanges() {
        val base = ReportManifest.contentHash(ReportManifest.content(ReportSample.data(StubSigner), pub))
        val other = ReportSample.data(StubSigner).let { it.copy(readings = it.readings.dropLast(1)) }
        assertNotEquals(base, ReportManifest.contentHash(ReportManifest.content(other, pub)))
    }

    @Test
    fun onlyCompletedTripsCanBeReported() {
        val active = ReportSample.data(StubSigner, trip = ReportSample.trip.copy(status = TripStatus.ACTIVE, endedUtcMs = null))
        assertFailsWith<IllegalArgumentException> { ReportManifest.content(active, pub) }
    }

    @Test
    fun contentCarriesEverythingTheVerifierNeedsAndNoPrivateData() {
        val text = CanonicalJson.encode(ReportManifest.content(ReportSample.data(StubSigner), pub))
        assertTrue(text.contains("\"mac_hash\":\"0123456789ab\""))
        assertTrue(text.contains("\"key_fingerprint\":\"${ReportSample.fingerprint(pub)}\""))
        assertTrue(text.contains("\"readings_digest\":\"${ReportSample.data(StubSigner).readingsDigest}\""))
        assertTrue(text.contains("\"canonical\":\"v=1\\ntrip=trip-report-0001\\nseq=1"))
        assertTrue(text.contains("\"lat\":\"-6.2088000\""))
        assertTrue(text.contains("\"lat\":null"), "an event without a fix keeps null, not 0")
    }

    @Test
    fun finalManifestIsSignedOverItsOwnHashAndBindsThePdf() {
        val data = ReportSample.data(JcaSigner(pair), pair.public)
        val content = ReportManifest.content(data, pub)
        val m = ReportManifest.finalize(content, pdf, generatedAtUtcMs = 99L, signer = JcaSigner(pair))
        assertEquals(ReportManifest.contentHash(content), m.contentHash)
        assertTrue(m.json.contains("\"sha256\":\"${pdf.sha256}\""))
        assertEquals(m.json, CanonicalJson.encode(parseTopLevel(m.json)), "file text is the canonical form")
        // The signature covers the ASCII hex of manifest_hash and verifies with the embedded key.
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(pair.public)
            update(m.manifestHash.toByteArray(Charsets.UTF_8))
            verify(Hex.decode(m.signatureHex)!!)
        }
        assertTrue(ok)
        // manifest_hash covers everything except the hash and signature themselves.
        val stripped = parseTopLevel(m.json) - "manifest_hash" - "manifest_signature"
        assertEquals(Hex.sha256(CanonicalJson.encode(stripped).toByteArray(Charsets.UTF_8)), m.manifestHash)
    }

    @Test
    fun differentPdfBytesGiveDifferentManifestHashButSameContentHash() {
        val content = ReportManifest.content(ReportSample.data(StubSigner), pub)
        val a = ReportManifest.finalize(content, pdf, 1L, StubSigner)
        val b = ReportManifest.finalize(content, pdf.copy(sha256 = "cd".repeat(32)), 1L, StubSigner)
        assertEquals(a.contentHash, b.contentHash)
        assertNotEquals(a.manifestHash, b.manifestHash)
    }

    @Test
    fun manifestStatesHonestlyThatThePdfItselfIsNotSigned() {
        val m = ReportManifest.finalize(ReportManifest.content(ReportSample.data(StubSigner), pub), pdf, 1L, StubSigner)
        assertTrue(m.json.contains("PAdES"))
        assertTrue(m.json.contains("tamper-evident"))
    }

    /** Minimal JSON reader for the canonical subset, to avoid a JSON dependency in :domain. */
    private fun parseTopLevel(json: String): Map<String, Any?> = JsonReaderForTests(json).readObject()
}
