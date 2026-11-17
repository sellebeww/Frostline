// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HashChainTest {
    private val pair = newKeyPair()
    private val signer = JcaSigner(pair)
    private val pub = pair.public

    private fun verify(events: List<CustodyEvent>) = ChainVerifier.verify(events, pub)
    private fun reason(events: List<CustodyEvent>) = assertIs<ChainVerification.Invalid>(verify(events)).reason

    @Test
    fun validChainPasses() {
        val chain = sampleChain(signer)
        assertEquals(listOf(1, 2, 3, 4), chain.map { it.seq })
        assertEquals(HashChain.GENESIS_PREV, chain[0].prevHash)
        assertEquals(chain[0].hash, chain[1].prevHash)
        assertEquals(ChainVerification.Valid, verify(chain))
    }

    @Test
    fun emptyAndInProgressChainsAreValid() {
        assertEquals(ChainVerification.Valid, verify(emptyList()))
        assertEquals(ChainVerification.Valid, verify(sampleChain(signer).take(2)))
    }

    private fun flip(s: String) = (if (s[0] == '0') "1" else "0") + s.substring(1)

    // One mutator per CustodyEvent field.
    private val mutators: Map<String, (CustodyEvent) -> CustodyEvent> = mapOf(
        "tripId" to { e -> e.copy(tripId = e.tripId + "x") },
        "seq" to { e -> e.copy(seq = e.seq + 1) },
        "type" to { e -> e.copy(type = if (e.type == CustodyType.HANDOFF) CustodyType.DELIVERY else CustodyType.HANDOFF) },
        "tagUid" to { e -> e.copy(tagUid = (e.tagUid ?: "") + "0") },
        "tagLabel" to { e -> e.copy(tagLabel = (e.tagLabel ?: "") + "x") },
        "tsUtcMs" to { e -> e.copy(tsUtcMs = e.tsUtcMs + 1) },
        "lat" to { e -> e.copy(lat = (e.lat ?: 0.0) + 0.000001) },
        "lon" to { e -> e.copy(lon = (e.lon ?: 0.0) + 0.000001) },
        "accuracyM" to { e -> e.copy(accuracyM = (e.accuracyM ?: 0.0) + 0.5) },
        "userId" to { e -> e.copy(userId = e.userId + "x") },
        "tempCenti" to { e -> e.copy(tempCenti = (e.tempCenti ?: 0) + 1) },
        "excursionOpen" to { e -> e.copy(excursionOpen = !e.excursionOpen) },
        "readingsDigest" to { e -> e.copy(readingsDigest = flip(e.readingsDigest)) },
        "prevHash" to { e -> e.copy(prevHash = flip(e.prevHash)) },
        "hash" to { e -> e.copy(hash = flip(e.hash)) },
        "signatureHex" to { e -> e.copy(signatureHex = flip(e.signatureHex)) },
    )

    @Test
    fun mutatorsCoverEveryField() {
        val fieldCount = CustodyEvent::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(fieldCount, mutators.size, "add a mutator for every new CustodyEvent field")
    }

    @Test
    fun changingAnySingleFieldOfAnyEventIsDetected() {
        val chain = sampleChain(signer)
        for ((name, mutate) in mutators) {
            for (i in chain.indices) {
                val tampered = chain.toMutableList().also { it[i] = mutate(it[i]) }
                assertTrue(verify(tampered) is ChainVerification.Invalid, "field $name of event $i not detected")
            }
        }
    }

    @Test
    fun settingOptionalFieldsToNullIsDetected() {
        val chain = sampleChain(signer)
        val nullers: List<(CustodyEvent) -> CustodyEvent> = listOf(
            { e -> e.copy(tagUid = null) }, { e -> e.copy(tagLabel = null) }, { e -> e.copy(lat = null) },
            { e -> e.copy(lon = null) }, { e -> e.copy(accuracyM = null) }, { e -> e.copy(tempCenti = null) },
        )
        for (n in nullers) for (i in chain.indices) {
            assertTrue(verify(chain.toMutableList().also { it[i] = n(it[i]) }) is ChainVerification.Invalid)
        }
    }

    @Test
    fun swappedOrderIsDetected() {
        val c = sampleChain(signer)
        assertTrue(verify(listOf(c[0], c[2], c[1], c[3])) is ChainVerification.Invalid)
    }

    @Test
    fun deletedMiddleEventIsDetected() {
        val c = sampleChain(signer)
        assertTrue(verify(listOf(c[0], c[2], c[3])) is ChainVerification.Invalid)
        assertEquals(VerifyReason.BAD_SEQ, reason(listOf(c[0], c[2], c[3])))
    }

    @Test
    fun insertedEventIsDetected() {
        val c = sampleChain(signer)
        assertTrue(verify(listOf(c[0], c[1], c[1], c[2], c[3])) is ChainVerification.Invalid)
        val forged = forge(fields(CustodyType.HANDOFF, 1_300_000L), 3, c[1].hash, signer)
        assertTrue(verify(listOf(c[0], c[1], forged, c[2], c[3])) is ChainVerification.Invalid)
    }

    @Test
    fun swappedSignatureIsDetected() {
        val c = sampleChain(signer)
        val t = c.toMutableList().also { it[1] = it[1].copy(signatureHex = c[2].signatureHex) }
        assertEquals(VerifyReason.BAD_SIGNATURE, reason(t))
    }

    @Test
    fun garbageSignatureIsDetectedWithoutThrowing() {
        val c = sampleChain(signer)
        for (bad in listOf("", "zz", "00", "abc")) {
            val t = c.toMutableList().also { it[0] = it[0].copy(signatureHex = bad) }
            assertEquals(VerifyReason.BAD_SIGNATURE, reason(t))
        }
    }

    @Test
    fun otherKeyIsDetected() {
        val c = sampleChain(signer)
        assertEquals(VerifyReason.BAD_SIGNATURE, assertIs<ChainVerification.Invalid>(ChainVerifier.verify(c, newKeyPair().public)).reason)
        val attacker = JcaSigner(newKeyPair())
        assertTrue(verify(sampleChain(attacker)) is ChainVerification.Invalid)
    }

    @Test
    fun firstEventMustBePickup() {
        val e1 = forge(fields(CustodyType.HANDOFF, 1L), 1, HashChain.GENESIS_PREV, signer)
        assertEquals(VerifyReason.FIRST_NOT_PICKUP, reason(listOf(e1)))
    }

    @Test
    fun pickupOnlyAsFirst() {
        val e1 = forge(fields(CustodyType.PICKUP, 1L), 1, HashChain.GENESIS_PREV, signer)
        val e2 = forge(fields(CustodyType.PICKUP, 2L), 2, e1.hash, signer)
        assertEquals(VerifyReason.PICKUP_NOT_FIRST, reason(listOf(e1, e2)))
    }

    @Test
    fun nothingMayFollowDelivery() {
        val c = sampleChain(signer)
        val extra = forge(fields(CustodyType.HANDOFF, 9_999_999L, trip = c[3].tripId), 5, c[3].hash, signer)
        assertEquals(VerifyReason.EVENT_AFTER_DELIVERY, reason(c + extra))
    }

    @Test
    fun mixedTripsAreDetected() {
        val e1 = forge(fields(CustodyType.PICKUP, 1L), 1, HashChain.GENESIS_PREV, signer)
        val e2 = forge(fields(CustodyType.HANDOFF, 2L, trip = "other"), 2, e1.hash, signer)
        assertEquals(VerifyReason.TRIP_MISMATCH, reason(listOf(e1, e2)))
    }

    @Test
    fun appendEventEnforcesRules() {
        val first = HashChain.appendEvent(null, fields(CustodyType.PICKUP, 1L), signer)
        assertFailsWith<IllegalArgumentException> { HashChain.appendEvent(null, fields(CustodyType.HANDOFF, 1L), signer) }
        assertFailsWith<IllegalArgumentException> { HashChain.appendEvent(first, fields(CustodyType.PICKUP, 2L), signer) }
        assertFailsWith<IllegalArgumentException> { HashChain.appendEvent(first, fields(CustodyType.HANDOFF, 2L, trip = "other"), signer) }
        val delivered = HashChain.appendEvent(first, fields(CustodyType.DELIVERY, 3L), signer)
        assertFailsWith<IllegalArgumentException> { HashChain.appendEvent(delivered, fields(CustodyType.HANDOFF, 4L), signer) }
    }
}
