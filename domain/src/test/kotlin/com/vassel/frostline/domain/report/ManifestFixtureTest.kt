// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.integrity.JcaSigner
import com.vassel.frostline.domain.integrity.newKeyPair
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixtures shared with the independent Python verifier (tools/tests). Two files:
 *  - `golden_content_stub.json` + `.sha256`: content built with a deterministic stub signer. It is compared byte for byte,
 *    so the manifest format cannot change silently, and pytest re-derives the same hash in Python.
 *  - `kotlin_v1/`: a real manifest signed with a fresh ECDSA key (signatures are random, so they are verified, not compared).
 * Regenerate with: FROSTLINE_UPDATE_FIXTURES=1 ./gradlew :domain:cleanTest :domain:test
 */
class ManifestFixtureTest {
    private val dir = File("../tools/tests/fixtures")
    private val stubKey = ByteArray(91) { it.toByte() } // arbitrary bytes: the golden content only embeds the key text

    private fun goldenContent() = CanonicalJson.encode(ReportManifest.content(ReportSample.data(StubSigner), stubKey))

    @Test
    fun generateWhenRequested() {
        if (System.getenv("FROSTLINE_UPDATE_FIXTURES") != "1") return
        File(dir, "kotlin_v1").mkdirs()
        val golden = goldenContent()
        File(dir, "golden_content_stub.json").writeText(golden)
        File(dir, "golden_content_stub.sha256").writeText(Hex.sha256(golden.toByteArray(Charsets.UTF_8)) + "\n")

        val pair = newKeyPair()
        val signer = JcaSigner(pair)
        val data = ReportSample.data(signer, pair.public)
        val pdf = "%PDF-1.4\nstub file standing in for the rendered PDF (hash binding test only)\n".toByteArray()
        val content = ReportManifest.content(data, pair.public.encoded)
        val manifest = ReportManifest.finalize(content, PdfBinding("report_stub.pdf", Hex.sha256(pdf), pdf.size.toLong()), 1_767_300_000_000L, signer)
        File(dir, "kotlin_v1/report.manifest.json").writeText(manifest.json)
        File(dir, "kotlin_v1/report_stub.pdf").writeBytes(pdf)
        File(dir, "kotlin_v1/readings.csv").writeText(CsvExport.readings(ReportSample.TRIP_ID, data.readings))
        File(dir, "kotlin_v1/excursions.csv").writeText(CsvExport.excursions(data.excursions))
    }

    @Test
    fun goldenContentIsUnchanged() {
        assertEquals(File(dir, "golden_content_stub.json").readText(), goldenContent(), "manifest content format changed")
    }

    @Test
    fun committedKotlinManifestIsSelfConsistent() {
        val text = File(dir, "kotlin_v1/report.manifest.json").readText()
        val m = JsonReaderForTests(text).readObject()
        assertEquals(text, CanonicalJson.encode(m), "file is in canonical form")
        @Suppress("UNCHECKED_CAST")
        val content = m["content"] as Map<String, Any?>
        assertEquals(m["content_hash"], ReportManifest.contentHash(content))
        val body = m - "manifest_hash" - "manifest_signature"
        assertEquals(m["manifest_hash"], Hex.sha256(CanonicalJson.encode(body).toByteArray(Charsets.UTF_8)))
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(content["public_key_x509_base64"] as String)))
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(pub); update((m["manifest_hash"] as String).toByteArray()); verify(Hex.decode(m["manifest_signature"] as String)!!)
        }
        assertTrue(ok)
        assertEquals(File(dir, "kotlin_v1/readings.csv").readText(), CsvExport.readings(ReportSample.TRIP_ID, ReportSample.readings))
    }
}
