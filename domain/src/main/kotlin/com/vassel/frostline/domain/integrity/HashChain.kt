// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.integrity

import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.port.Signer

object HashChain {
    val GENESIS_PREV: String = "0".repeat(64)

    /** Creates the next event after [prev] (null for the first). Enforces PICKUP-first, DELIVERY-last and one trip. */
    fun appendEvent(prev: CustodyEvent?, fields: CustodyFields, signer: Signer): CustodyEvent {
        if (prev == null) {
            require(fields.type == CustodyType.PICKUP) { "The first custody event must be PICKUP" }
        } else {
            require(prev.type != CustodyType.DELIVERY) { "No event may follow DELIVERY" }
            require(fields.type != CustodyType.PICKUP) { "PICKUP is only allowed as the first event" }
            require(prev.tripId == fields.tripId) { "Event belongs to a different trip" }
        }
        val seq = (prev?.seq ?: 0) + 1
        val prevHash = prev?.hash ?: GENESIS_PREV
        val canonical = Canonicalizer.eventCanonical(fields, seq, prevHash).toByteArray(Charsets.UTF_8)
        return CustodyEvent(
            fields.tripId, seq, fields.type, fields.tagUid, fields.tagLabel, fields.tsUtcMs, fields.lat, fields.lon,
            fields.accuracyM, fields.userId, fields.tempCenti, fields.excursionOpen, fields.readingsDigest,
            prevHash, Hex.sha256(canonical), Hex.encode(signer.sign(canonical)),
        )
    }
}
