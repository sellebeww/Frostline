// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.port.Signer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

fun newKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

class JcaSigner(private val pair: KeyPair) : Signer {
    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(data)
            sign()
        }
}

fun fields(
    type: CustodyType,
    ts: Long,
    tag: String? = "04a1b2c3",
    label: String? = "Tag A",
    lat: Double? = -6.2,
    lon: Double? = 106.816666,
    acc: Double? = 12.5,
    temp: Int? = 523,
    excursionOpen: Boolean = false,
    digest: String = Canonicalizer.readingsDigest(emptyList()),
    trip: String = "trip-1",
) = CustodyFields(trip, type, tag, label, ts, lat, lon, acc, "petugas-01", temp, excursionOpen, digest)

/** PICKUP, HANDOFF, HANDOFF, DELIVERY. */
fun sampleChain(signer: Signer): List<CustodyEvent> {
    val types = listOf(CustodyType.PICKUP, CustodyType.HANDOFF, CustodyType.HANDOFF, CustodyType.DELIVERY)
    val out = mutableListOf<CustodyEvent>()
    types.forEachIndexed { i, t ->
        out += HashChain.appendEvent(out.lastOrNull(), fields(t, 1_000_000L + i * 600_000L, tag = "04a1b2c$i", label = "Tag $i", temp = 500 + i), signer)
    }
    return out
}

/** Builds a signed event without the rules of [HashChain.appendEvent], to test the verifier alone. */
fun forge(f: CustodyFields, seq: Int, prev: String, signer: Signer): CustodyEvent {
    val canonical = Canonicalizer.eventCanonical(f, seq, prev).toByteArray(Charsets.UTF_8)
    return CustodyEvent(
        f.tripId, seq, f.type, f.tagUid, f.tagLabel, f.tsUtcMs, f.lat, f.lon, f.accuracyM, f.userId,
        f.tempCenti, f.excursionOpen, f.readingsDigest, prev, Hex.sha256(canonical), Hex.encode(signer.sign(canonical)),
    )
}
