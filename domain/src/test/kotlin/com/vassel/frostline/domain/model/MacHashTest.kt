// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MacHashTest {
    @Test
    fun goldenValueIsTwelveHexCharsOfSha256() {
        assertEquals("261900fb1113", MacHash.of("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun caseAndWhitespaceDoNotMatterButTheAddressDoes() {
        assertEquals(MacHash.of("AA:BB:CC:DD:EE:FF"), MacHash.of(" aa:bb:cc:dd:ee:ff "))
        assertNotEquals(MacHash.of("AA:BB:CC:DD:EE:FF"), MacHash.of("AA:BB:CC:DD:EE:00"))
    }
}
