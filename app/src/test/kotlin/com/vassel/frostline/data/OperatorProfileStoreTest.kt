// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OperatorProfileStoreTest {
    private fun store() = OperatorProfileStore(RuntimeEnvironment.getApplication())

    @Test
    fun noProfileUntilCreatedThenItPersistsWithAUuid() {
        assertNull(store().get())
        val saved = store().save("  Sari ", "Petugas imunisasi")!!
        assertEquals("Sari", saved.name)
        assertEquals(36, saved.userId.length)
        assertEquals(saved, store().get()) // a new instance reads it back
    }

    @Test
    fun editingKeepsTheUserIdSoEarlierEventsStayAttributed() {
        val first = store().save("Sari", "A")!!
        val second = store().save("Sari W", "B")!!
        assertEquals(first.userId, second.userId)
        assertNotEquals(first.name, second.name)
    }

    @Test
    fun aBlankNameIsRejected() {
        assertNull(store().save("   ", "x"))
        assertNull(store().get())
    }
}
