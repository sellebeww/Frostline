// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import android.content.pm.ApplicationInfo
import com.vassel.frostline.R
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchemaAndConfigTest {
    @Test
    fun exportedSchemaMatchesTheCurrentDatabaseVersion() {
        val file = File("schemas/com.vassel.frostline.data.db.FrostlineDatabase/${FrostlineDatabase.VERSION}.json")
        assertTrue(file.exists(), "export the schema for version ${FrostlineDatabase.VERSION}")
        val text = file.readText()
        assertTrue(text.contains("\"version\": ${FrostlineDatabase.VERSION}"))
        for (table in listOf("carriers", "sensors", "threshold_profiles", "trips", "readings", "custody_events", "excursions", "reports")) {
            assertTrue(text.contains("\"tableName\": \"$table\""), "missing table $table")
        }
    }

    @Test
    fun everyVersionStepHasAMigrationAndTheirSchemasAreExported() {
        for (from in 1 until FrostlineDatabase.VERSION) {
            assertTrue(Migrations.ALL.any { it.startVersion == from && it.endVersion == from + 1 }, "no migration $from to ${from + 1}")
        }
        assertEquals(FrostlineDatabase.VERSION - 1, Migrations.ALL.size)
        for (v in 1..FrostlineDatabase.VERSION) assertTrue(File("schemas/com.vassel.frostline.data.db.FrostlineDatabase/$v.json").exists())
    }

    @Test
    fun backupIsDisabledAndExtractionRulesExcludeDatabaseAndFiles() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val excluded = mutableListOf<String>()
        context.resources.getXml(R.xml.data_extraction_rules).use { xml ->
            var section = ""
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                if (xml.name == "cloud-backup" || xml.name == "device-transfer") section = xml.name
                if (xml.name == "exclude") excluded += section + ":" + xml.getAttributeValue(null, "domain")
            }
        }
        for (s in listOf("cloud-backup", "device-transfer")) {
            assertTrue(excluded.contains("$s:database"), "$s must exclude database")
            assertTrue(excluded.contains("$s:file"), "$s must exclude files")
        }
    }

    @Test
    fun productionSourcesDoNotLog() {
        val offenders = File("src/main").walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { it.contains("android.util.Log") || it.contains("Log.d(") || it.contains("println(") || it.contains("Timber") } }
            .toList()
        assertFalse(offenders.isNotEmpty(), "logging found in $offenders")
    }
}
