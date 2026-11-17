// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.db.Migrations
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Creates each exported older schema with sample rows and checks that the real migrations keep them. */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FrostlineDatabase::class.java)

    @Test
    fun migration1To2KeepsCustodyRowsAndAddsAnEmptyDiagnosticColumn() {
        helper.createDatabase(NAME, 1).apply {
            execSQL("INSERT INTO threshold_profiles (id, name, min_c, max_c, freeze_c) VALUES ('p1', 'p', 2.0, 8.0, 0.0)")
            execSQL(
                "INSERT INTO trips (id, status, threshold_profile_id, operator_id, started_utc_ms, log_interval_sec) " +
                    "VALUES ('t1', 'ACTIVE', 'p1', 'op', 1, 60)",
            )
            execSQL(
                "INSERT INTO custody_events (id, trip_id, seq, type, user_id, ts_utc_ms, excursion_open, readings_digest, prev_hash, hash, signature) " +
                    "VALUES ('e1', 't1', 1, 'PICKUP', 'op', 1, 0, 'd', 'p', 'h', 's')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(NAME, 2, true, *Migrations.ALL)
        db.query("SELECT hash, signature, diagnostic FROM custody_events WHERE id = 'e1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("h", it.getString(0))
            assertEquals("s", it.getString(1))
            assertNull(it.getString(2))
        }
        // the immutability triggers are installed by Room's open callback, not by the migration
        db.close()
    }

    @Test
    fun currentSchemaCreatesAndValidates() {
        helper.createDatabase(NAME_CURRENT, FrostlineDatabase.VERSION).close()
        helper.runMigrationsAndValidate(NAME_CURRENT, FrostlineDatabase.VERSION, true, *Migrations.ALL).close()
    }

    private companion object {
        const val NAME = "migration-test"
        const val NAME_CURRENT = "migration-test-current"
    }
}
