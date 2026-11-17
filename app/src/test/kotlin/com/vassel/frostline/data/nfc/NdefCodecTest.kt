// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.nfc

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class NdefCodecTest {
    private val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

    private fun valid(uri: String?) = assertIs<TagDecode.Valid>(NdefCodec.decode(uri)).info
    private fun foreign(uri: String?) = assertIs<TagDecode.Foreign>(NdefCodec.decode(uri)).reason

    @Test
    fun encodesTheDocumentedFormat() {
        assertEquals("frostline://tag/123e4567-e89b-12d3-a456-426614174000?label=Gudang%20Dinkes", NdefCodec.encode(id, "Gudang Dinkes"))
    }

    @Test
    fun roundTripsIdAndLabel() {
        assertEquals(TagInfo(id.toString(), "Puskesmas A"), valid(NdefCodec.encode(id, "Puskesmas A")))
    }

    @Test
    fun specialCharactersSurviveTheRoundTrip() {
        for (label in listOf("A&B=C", "Pos #1?", "100% aman", "a+b", "Müller Ünit", "Kantor/Desa \"Satu\"", "日本語", "x%zz", "tab-free  spaced")) {
            assertEquals(label.trim(), valid(NdefCodec.encode(id, label)).label, label)
        }
    }

    @Test
    fun emojiCountsAsOneCharacterButMustStillFitTheTag() {
        assertEquals("📦", valid(NdefCodec.encode(id, "📦")).label)
        assertFailsWith<IllegalArgumentException> { NdefCodec.encode(id, "📦".repeat(20)) }
    }

    @Test
    fun theLabelIsTrimmedAndUppercaseUuidsAreNormalised() {
        assertEquals("Gudang", valid(NdefCodec.encode(id, "  Gudang ")).label)
        assertEquals(id.toString(), valid("frostline://tag/123E4567-E89B-12D3-A456-426614174000?label=x").tagId)
        assertEquals("x", valid("FROSTLINE://TAG/123e4567-e89b-12d3-a456-426614174000?label=x").label)
    }

    @Test
    fun foreignSchemesAndPayloadsAreRejected() {
        assertEquals(ForeignReason.NOT_FROSTLINE, foreign("https://example.com/tag/123e4567-e89b-12d3-a456-426614174000?label=x"))
        assertEquals(ForeignReason.NOT_FROSTLINE, foreign("frostline2://tag/123e4567-e89b-12d3-a456-426614174000?label=x"))
        assertEquals(ForeignReason.NOT_FROSTLINE, foreign("frostline://other/123e4567-e89b-12d3-a456-426614174000?label=x"))
        assertEquals(ForeignReason.NOT_FROSTLINE, foreign("tel:+628123"))
    }

    @Test
    fun emptyOrMissingPayloadIsEmpty() {
        assertEquals(ForeignReason.EMPTY, foreign(null))
        assertEquals(ForeignReason.EMPTY, foreign(""))
    }

    @Test
    fun malformedFrostlineUrisAreRejected() {
        val ok = "frostline://tag/123e4567-e89b-12d3-a456-426614174000"
        for (bad in listOf(
            "frostline://tag/not-a-uuid?label=x", "frostline://tag/?label=x", ok, "$ok?", "$ok?label=", "$ok?label=%20%20",
            "$ok?name=x", "$ok?label=x&extra=1", "$ok?label=a&label=b", "$ok?label=%zz", "$ok?label=a%0Ab", "$ok?label=x?y",
            "$ok?label=a=b", "$ok/extra?label=x",
        )) {
            assertEquals(ForeignReason.MALFORMED, foreign(bad), bad)
        }
    }

    @Test
    fun anOverlongUriIsRejected() {
        assertEquals(ForeignReason.TOO_LONG, foreign("frostline://tag/123e4567-e89b-12d3-a456-426614174000?label=" + "a".repeat(80)))
    }

    @Test
    fun labelRulesAreEnforcedOnEncode() {
        assertEquals(NdefCodec.LabelProblem.EMPTY, NdefCodec.validateLabel("   "))
        assertEquals(NdefCodec.LabelProblem.TOO_LONG, NdefCodec.validateLabel("a".repeat(33)))
        assertNull(NdefCodec.validateLabel("a".repeat(32)))
        assertEquals(NdefCodec.LabelProblem.CONTROL_CHARACTER, NdefCodec.validateLabel("a\nb"))
        assertFailsWith<IllegalArgumentException> { NdefCodec.encode(id, "") }
        assertFailsWith<IllegalArgumentException> { NdefCodec.encode(id, "a\u0000b") }
    }

    @Test
    fun theLongestAllowedAsciiLabelStillFitsNtag213() {
        val uri = NdefCodec.encode(id, "a".repeat(32))
        assert(uri.toByteArray().size <= NdefCodec.MAX_URI_BYTES)
        assertEquals("a".repeat(32), valid(uri).label)
    }

    @Test
    fun chipUidIsOnlyAShortDiagnosticHash() {
        val h = TagUid.hash(byteArrayOf(4, 1, 2, 3, 4, 5, 6))
        assertEquals(12, h.length)
        assertEquals(h, TagUid.hash(byteArrayOf(4, 1, 2, 3, 4, 5, 6)))
        assert(h != TagUid.hash(byteArrayOf(4, 1, 2, 3, 4, 5, 7)))
        assertEquals(12, TagUid.hash(null).length)
    }
}
