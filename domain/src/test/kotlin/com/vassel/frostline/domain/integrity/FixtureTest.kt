// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.model.Reading
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fixture for the independent Python verifier (Phase 7). Hashes and canonical text are deterministic and are checked
 * against the committed file on every run, so the format cannot change silently. Signatures are random, so the committed
 * ones are verified with the committed public key instead of compared.
 * Regenerate with: FROSTLINE_UPDATE_FIXTURES=1 ./gradlew :domain:cleanTest :domain:test
 */
class FixtureTest {
    private val resource = "/fixtures/custody_chain_v1.json"
    private val tripId = "trip-fixture-0001"
    private val t0 = 1_767_225_600_000L

    private val readings: List<Reading> = (0 until 31).map { i ->
        val temp = when (i) { in 10..14 -> 9.0 + (i - 10) * 0.1; else -> 5.0 + (i % 3) * 0.1 }
        Reading(t0 + i * 60_000L, 1_000_000L + i * 60_000L, temp)
    }

    private fun fieldsFor(type: CustodyType, minute: Int, tag: String, label: String, lat: Double, lon: Double, user: String) = run {
        val ts = t0 + minute * 60_000L
        val upTo = readings.filter { it.tsUtcMs <= ts }
        com.vassel.frostline.domain.model.CustodyFields(
            tripId, type, tag, label, ts, lat, lon, 9.5, user, upTo.lastOrNull()?.tempCenti,
            excursionOpen = minute in 11..14, Canonicalizer.readingsDigest(upTo),
        )
    }

    private fun build(signer: com.vassel.frostline.domain.port.Signer): List<CustodyEvent> {
        val specs = listOf(
            fieldsFor(CustodyType.PICKUP, 0, "04a1b2c3d4e5f6", "Gudang Dinkes", -6.2088, 106.8456, "petugas-01"),
            fieldsFor(CustodyType.HANDOFF, 12, "04112233445566", "Puskesmas A/B", -6.2150, 106.8500, "petugas-01"),
            fieldsFor(CustodyType.DELIVERY, 30, "04aabbccddeeff", "Posyandu Melati 100%", -6.2200, 106.8600, "petugas-02"),
        )
        val out = mutableListOf<CustodyEvent>()
        specs.forEach { out += HashChain.appendEvent(out.lastOrNull(), it, signer) }
        return out
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun json(pub: PublicKey, events: List<CustodyEvent>): String {
        fun s(v: String?) = if (v == null) "null" else "\"${esc(v)}\""
        val sb = StringBuilder()
        sb.append("{\n  \"version\": 1,\n  \"public_key_x509_hex\": \"${Hex.encode(pub.encoded)}\",\n  \"readings\": [\n")
        sb.append(readings.joinToString(",\n") { "    {\"ts\": ${it.tsUtcMs}, \"temp_centi\": ${it.tempCenti}}" })
        sb.append("\n  ],\n  \"events\": [\n")
        sb.append(events.joinToString(",\n") { e ->
            val canon = Canonicalizer.eventCanonical(e.toFields(), e.seq, e.prevHash)
            listOf(
                "    {", "      \"seq\": ${e.seq},", "      \"type\": ${s(e.type.name)},", "      \"trip\": ${s(e.tripId)},",
                "      \"tag\": ${s(e.tagUid)},", "      \"tag_label\": ${s(e.tagLabel)},", "      \"ts\": ${e.tsUtcMs},",
                "      \"lat\": ${e.lat},", "      \"lon\": ${e.lon},", "      \"acc\": ${e.accuracyM},", "      \"user\": ${s(e.userId)},",
                "      \"temp_centi\": ${e.tempCenti},", "      \"excursion_open\": ${e.excursionOpen},",
                "      \"readings_digest\": ${s(e.readingsDigest)},", "      \"prev\": ${s(e.prevHash)},",
                "      \"canonical\": ${s(canon)},", "      \"hash\": ${s(e.hash)},", "      \"signature\": ${s(e.signatureHex)}", "    }",
            ).joinToString("\n")
        })
        sb.append("\n  ]\n}\n")
        return sb.toString()
    }

    @Test
    fun generateWhenRequested() {
        if (System.getenv("FROSTLINE_UPDATE_FIXTURES") != "1") return
        val pair = newKeyPair()
        val file = File("src/test/resources/fixtures/custody_chain_v1.json")
        file.parentFile.mkdirs()
        file.writeText(json(pair.public, build(JcaSigner(pair))))
    }

    @Test
    fun committedFixtureMatchesCurrentFormatAndVerifies() {
        val text = assertNotNull(javaClass.getResource(resource), "fixture missing, regenerate it").readText()
        val pubHex = Regex("\"public_key_x509_hex\": \"([0-9a-f]+)\"").find(text)!!.groupValues[1]
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Hex.decode(pubHex)!!))
        val sigs = Regex("\"signature\": \"([0-9a-f]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        val expected = build(JcaSigner(newKeyPair())) // only hash/canonical are compared
        assertEquals(expected.size, sigs.size)
        val rebuilt = expected.mapIndexed { i, e -> e.copy(signatureHex = sigs[i]) }
        for (e in rebuilt) {
            assertTrue(text.contains("\"hash\": \"${e.hash}\""), "hash of seq ${e.seq} changed")
            val canon = Canonicalizer.eventCanonical(e.toFields(), e.seq, e.prevHash)
            assertTrue(text.contains("\"canonical\": \"${esc(canon)}\""), "canonical of seq ${e.seq} changed")
        }
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(rebuilt, pub))
    }
}
