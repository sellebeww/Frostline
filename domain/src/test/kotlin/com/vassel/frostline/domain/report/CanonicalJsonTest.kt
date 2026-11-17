// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalJsonTest {
    @Test
    fun keysAreSortedAndOutputIsCompact() {
        val v = mapOf("b" to 1L, "a" to listOf(true, null, "x"), "c" to mapOf("z" to 2, "y" to false))
        assertEquals("""{"a":[true,null,"x"],"b":1,"c":{"y":false,"z":2}}""", CanonicalJson.encode(v))
    }

    @Test
    fun stringsEscapeLikeCompactJsonWithoutAsciiEscaping() {
        // Same rules as Python json.dumps(ensure_ascii=False): quote, backslash, \n \r \t \b \f, other controls as \u00xx.
        val s = "a\"b\\c\nd\re\tf\u0008g\u000ch\u0001i é ü 日本 😀 /"
        assertEquals("\"a\\\"b\\\\c\\nd\\re\\tf\\bg\\fh\\u0001i é ü 日本 😀 /\"", CanonicalJson.encode(s))
    }

    @Test
    fun floatingPointNumbersAreRejectedBecauseTheirTextIsNotPortable() {
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(mapOf("x" to 1.5)) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(mapOf("x" to 1.5f)) }
    }

    @Test
    fun nonStringKeysAndUnknownTypesAreRejected() {
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(mapOf(1 to 2)) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(object {}) }
    }

    @Test
    fun negativeAndLargeIntegersKeepPlainDigits() {
        assertEquals("[-5,1767225600000,0]", CanonicalJson.encode(listOf(-5, 1_767_225_600_000L, 0)))
    }
}
