// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.nfc

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

data class TagInfo(val tagId: String, val label: String)

enum class ForeignReason { EMPTY, NOT_FROSTLINE, MALFORMED, TOO_LONG, UNREADABLE }

sealed interface TagDecode {
    data class Valid(val info: TagInfo) : TagDecode
    data class Foreign(val reason: ForeignReason) : TagDecode
}

/**
 * The tag format: one NDEF URI record "frostline://tag/<uuid>?label=<percent-encoded name>". Identity is the UUID in
 * the record, never the chip UID (chip UIDs of cheap tags can be cloned or reprogrammed). Anything else is foreign.
 */
object NdefCodec {
    const val PREFIX = "frostline://tag/"
    const val MAX_LABEL_CHARS = 32

    /** Keeps the whole NDEF message inside the 144 byte user memory of the smallest common tag (NTAG213). */
    const val MAX_URI_BYTES = 120

    private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    enum class LabelProblem { EMPTY, TOO_LONG, CONTROL_CHARACTER }

    fun validateLabel(raw: String): LabelProblem? {
        val label = raw.trim()
        return when {
            label.isEmpty() -> LabelProblem.EMPTY
            label.codePointCount(0, label.length) > MAX_LABEL_CHARS -> LabelProblem.TOO_LONG
            label.any { Character.isISOControl(it) } -> LabelProblem.CONTROL_CHARACTER
            else -> null
        }
    }

    /** Throws [IllegalArgumentException] for an invalid label or a URI that would not fit. */
    fun encode(tagId: UUID, label: String): String {
        require(validateLabel(label) == null) { "Invalid label" }
        val encoded = URLEncoder.encode(label.trim(), "UTF-8").replace("+", "%20")
        val uri = PREFIX + tagId.toString() + "?label=" + encoded
        require(uri.toByteArray(Charsets.UTF_8).size <= MAX_URI_BYTES) { "Label too long for the tag" }
        return uri
    }

    fun decode(uri: String?): TagDecode {
        if (uri.isNullOrEmpty()) return TagDecode.Foreign(ForeignReason.EMPTY)
        if (!uri.startsWith(PREFIX, ignoreCase = true)) return TagDecode.Foreign(ForeignReason.NOT_FROSTLINE)
        if (uri.toByteArray(Charsets.UTF_8).size > MAX_URI_BYTES) return TagDecode.Foreign(ForeignReason.TOO_LONG)
        val rest = uri.substring(PREFIX.length)
        val parts = rest.split('?')
        if (parts.size != 2 || !UUID_REGEX.matches(parts[0])) return malformed()
        val query = parts[1]
        if (!query.startsWith("label=") || query.indexOf('&') >= 0 || query.indexOf('=', 6) >= 0) return malformed()
        val label = try {
            URLDecoder.decode(query.substring(6), "UTF-8").trim()
        } catch (_: IllegalArgumentException) {
            return malformed()
        }
        if (validateLabel(label) != null) return malformed()
        return TagDecode.Valid(TagInfo(UUID.fromString(parts[0]).toString(), label))
    }

    private fun malformed() = TagDecode.Foreign(ForeignReason.MALFORMED)
}

object TagUid {
    /** First 12 hex characters of SHA-256 over the chip UID. Diagnostic only: it is never used as an identity. */
    fun hash(uid: ByteArray?): String =
        MessageDigest.getInstance("SHA-256").digest(uid ?: ByteArray(0)).joinToString("") { "%02x".format(it) }.take(12)
}
