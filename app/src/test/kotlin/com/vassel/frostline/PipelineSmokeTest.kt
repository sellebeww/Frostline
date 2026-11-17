// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PipelineSmokeTest {
    @Test
    fun applicationIdMatchesContract() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("com.vassel.frostline", context.packageName)
    }

    @Test
    fun mockkWorks() {
        val fake = mockk<Runnable>(relaxed = true)
        every { fake.run() } returns Unit
        fake.run()
    }
}
