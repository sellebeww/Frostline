// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyType
import java.security.PublicKey
import java.security.Signature
import java.security.GeneralSecurityException

enum class VerifyReason { BAD_SEQ, TRIP_MISMATCH, BAD_PREV, BAD_HASH, BAD_SIGNATURE, FIRST_NOT_PICKUP, PICKUP_NOT_FIRST, EVENT_AFTER_DELIVERY }

sealed interface ChainVerification {
    data object Valid : ChainVerification
    data class Invalid(val seq: Int, val reason: VerifyReason) : ChainVerification
}

object ChainVerifier {
    /** An empty chain, or one still in progress (no DELIVERY yet), is valid. */
    fun verify(events: List<CustodyEvent>, publicKey: PublicKey): ChainVerification {
        var prev: CustodyEvent? = null
        for ((index, e) in events.withIndex()) {
            fun bad(r: VerifyReason) = ChainVerification.Invalid(e.seq, r)
            if (e.seq != index + 1) return bad(VerifyReason.BAD_SEQ)
            if (prev == null && e.type != CustodyType.PICKUP) return bad(VerifyReason.FIRST_NOT_PICKUP)
            if (prev != null) {
                if (prev.type == CustodyType.DELIVERY) return bad(VerifyReason.EVENT_AFTER_DELIVERY)
                if (e.type == CustodyType.PICKUP) return bad(VerifyReason.PICKUP_NOT_FIRST)
                if (e.tripId != prev.tripId) return bad(VerifyReason.TRIP_MISMATCH)
            }
            if (e.prevHash != (prev?.hash ?: HashChain.GENESIS_PREV)) return bad(VerifyReason.BAD_PREV)
            val canonical = Canonicalizer.eventCanonical(e.toFields(), e.seq, e.prevHash).toByteArray(Charsets.UTF_8)
            if (e.hash != Hex.sha256(canonical)) return bad(VerifyReason.BAD_HASH)
            if (!signatureValid(canonical, e.signatureHex, publicKey)) return bad(VerifyReason.BAD_SIGNATURE)
            prev = e
        }
        return ChainVerification.Valid
    }

    private fun signatureValid(data: ByteArray, signatureHex: String, key: PublicKey): Boolean {
        val sig = Hex.decode(signatureHex) ?: return false
        return try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(data)
                verify(sig)
            }
        } catch (_: GeneralSecurityException) {
            false
        }
    }
}
