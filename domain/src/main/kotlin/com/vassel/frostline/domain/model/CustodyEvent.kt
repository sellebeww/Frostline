// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

enum class CustodyType { PICKUP, HANDOFF, DELIVERY }

/** Input of a custody event before sequencing, hashing and signing. */
data class CustodyFields(
    val tripId: String,
    val type: CustodyType,
    val tagUid: String?,
    val tagLabel: String?,
    val tsUtcMs: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Double?,
    val userId: String,
    val tempCenti: Int?,
    val excursionOpen: Boolean,
    val readingsDigest: String,
)

/** Append-only, hash-chained and signed. [hash] and [signatureHex] are lowercase hex. */
data class CustodyEvent(
    val tripId: String,
    val seq: Int,
    val type: CustodyType,
    val tagUid: String?,
    val tagLabel: String?,
    val tsUtcMs: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Double?,
    val userId: String,
    val tempCenti: Int?,
    val excursionOpen: Boolean,
    val readingsDigest: String,
    val prevHash: String,
    val hash: String,
    val signatureHex: String,
) {
    fun toFields() = CustodyFields(
        tripId, type, tagUid, tagLabel, tsUtcMs, lat, lon, accuracyM, userId, tempCenti, excursionOpen, readingsDigest,
    )
}
