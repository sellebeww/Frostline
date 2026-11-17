// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 8 audit guards: cheap source scans that keep fixed findings from coming back. */
class RepoGuardsTest {
    private val main = File("src/main")
    private fun kotlinFiles(vararg roots: String) =
        roots.flatMap { File(it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }

    private fun offenders(files: List<File>, bad: (String) -> Boolean) =
        files.flatMap { f -> f.readLines().withIndex().filter { bad(it.value) }.map { "${f.path}:${it.index + 1}" } }

    @Test
    fun composeAndNotificationCodeHaveNoHardcodedUserFacingText() {
        val literal = Regex("""(Text\(\s*"|text\s*=\s*"|contentDescription\s*=\s*"|setContentTitle\("|setContentText\("|Toast\.makeText\([^,]+,\s*")""")
        val found = offenders(kotlinFiles("src/main/kotlin/com/vassel/frostline/ui", "src/main/kotlin/com/vassel/frostline/notification")) { literal.containsMatchIn(it) }
        assertTrue(found.isEmpty(), "hardcoded UI text, use strings.xml: $found")
    }

    @Test
    fun productionCodeHasNoNonNullAssertionsGlobalScopeOrRunBlocking() {
        val found = offenders(kotlinFiles("src/main", "../domain/src/main", "src/debug")) {
            val code = it.substringBefore("//")
            code.contains("!!") && !code.contains("!!=") || code.contains("GlobalScope") || code.contains("runBlocking")
        }
        assertTrue(found.isEmpty(), "forbidden construct: $found")
    }

    @Test
    fun noEmDashAnywhereInSourcesDocsOrTools() {
        val emDash = 0x2014.toChar().toString() // built from the code point so this file stays clean
        val roots = listOf("src", "../domain/src", "../tools", "../docs", "../README.md")
        val found = roots.flatMap { r -> File(r).walkTopDown().filter { it.isFile && !it.path.contains("__pycache__") && !it.name.endsWith(".pyc") && !it.name.endsWith(".json") }.toList() }
            .filter { f -> runCatching { f.readText().contains(emDash) }.getOrDefault(false) }
        assertTrue(found.isEmpty(), "em dash found in $found")
    }

    @Test
    fun manifestRequestsExactlyTheExpectedPermissionsAndExportsOnlyTheLauncher() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val permissions = Regex("""<uses-permission[^>]*android:name="android\.permission\.(\w+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                "NFC", "POST_NOTIFICATIONS", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_CONNECTED_DEVICE",
            ),
            permissions,
        )
        // Legacy Bluetooth permissions only matter up to API 30.
        for (legacy in listOf("BLUETOOTH", "BLUETOOTH_ADMIN")) {
            assertTrue(Regex("""android\.permission\.$legacy"\s+android:maxSdkVersion="30"""").containsMatchIn(manifest), "$legacy needs maxSdkVersion=30")
        }
        assertEquals(1, Regex("""android:exported="true"""").findAll(manifest).count(), "only MainActivity may be exported")
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun releaseBuildRunsR8WithResourceShrinkingAndKeepsSqlCipher() {
        val gradle = File("build.gradle.kts").readText()
        val release = gradle.substringAfter("release {").substringBefore("}")
        assertTrue(release.contains("isMinifyEnabled = true") && release.contains("isShrinkResources = true"))
        assertTrue(File("proguard-rules.pro").readText().contains("-keep class net.zetetic.database.** { *; }"))
        assertTrue(!gradle.contains("signingConfigs") && !gradle.contains("storePassword"), "no signing material in the repo")
    }

    @Test
    fun everyStringMentioningWhoPqsDeniesBeingAReplacement() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val mentions = Regex("""<string name="[^"]+">([^<]*PQS[^<]*)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(strings).map { it.groupValues[1] }.toList()
        assertTrue(mentions.isNotEmpty())
        for (m in mentions) assertTrue(m.contains("bukan", ignoreCase = true), "must negate a WHO PQS claim: $m")
    }

    @Test
    fun safeStatusLabelDoesNotClaimTheVaccineIsSafe() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val label = Regex("""<string name="status_safe">([^<]*)</string>""").find(strings)!!.groupValues[1]
        assertTrue(!label.contains("AMAN", ignoreCase = true), "'$label' implies the vaccine is safe; the app only knows the temperature is in range")
    }
}
