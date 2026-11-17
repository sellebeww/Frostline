// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vassel.frostline.data.db.DatabaseFailure
import com.vassel.frostline.data.db.DatabaseState
import com.vassel.frostline.data.db.EncryptedDatabase
import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.db.RoomTripRepository
import com.vassel.frostline.data.security.DatabaseKeyManager
import com.vassel.frostline.data.security.KeystoreSecretWrapper
import com.vassel.frostline.domain.model.ThresholdProfile
import com.vassel.frostline.domain.model.Trip
import com.vassel.frostline.domain.model.TripStatus
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Real SQLCipher and real Android Keystore. Uses its own database file, key file and wrap alias. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "frostline_instrumented_test.db"
    private val keyFile = File(context.noBackupFilesDir, "instrumented_test_key.bin")
    private val keyManager = DatabaseKeyManager(keyFile, KeystoreSecretWrapper("frostline_instrumented_test_wrap"))

    @Before
    fun clean() = wipe()

    @After
    fun tearDown() = wipe()

    private fun wipe() {
        context.deleteDatabase(dbName)
        keyFile.delete()
        File(keyFile.path + ".tmp").delete()
    }

    private fun open() = assertIs<DatabaseState.Ready>(EncryptedDatabase.open(context, keyManager, dbName)).database

    private fun trip() = Trip("t1", null, "op", 1L, null, TripStatus.ACTIVE, 60, ThresholdProfile())

    @Test
    fun dataSurvivesReopenWithTheSameKey() = runBlocking {
        val db = open()
        RoomTripRepository(db).create(trip())
        db.close()
        val again = open()
        assertEquals(trip(), RoomTripRepository(again).get("t1"))
        again.close()
    }

    @Test
    fun theFileIsNotPlainSqliteAndCannotBeReadWithoutThePassphrase() = runBlocking {
        val db = open()
        RoomTripRepository(db).create(trip())
        db.close()
        val path = context.getDatabasePath(dbName)
        val header = path.inputStream().use { it.readNBytes(16) }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.ISO_8859_1))
        assertTrue(!path.readBytes().toString(Charsets.ISO_8859_1).contains("COMPLETED") && !path.readBytes().toString(Charsets.ISO_8859_1).contains("threshold_profiles"))
        // the framework (plaintext) SQLite cannot open it
        assertFailsWith<SQLiteException> {
            android.database.sqlite.SQLiteDatabase.openDatabase(path.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT count(*) FROM sqlite_master", null).use { c -> c.moveToFirst() }
            }
        }
        // and so does SQLCipher with a wrong passphrase
        val wrong = FrostlineDatabase.create(context, dbName, SupportOpenHelperFactory(ByteArray(32) { 7 }, null, false))
        assertFailsWith<SQLiteException> { wrong.openHelper.writableDatabase }
        wrong.close()
    }

    @Test
    fun aLostKeyFileIsAnErrorAndLeavesTheDatabaseUntouched() = runBlocking {
        val db = open()
        RoomTripRepository(db).create(trip())
        db.close()
        val path = context.getDatabasePath(dbName)
        val before = path.readBytes()
        keyFile.delete()
        val state = EncryptedDatabase.open(context, keyManager, dbName)
        assertEquals(DatabaseState.Failed(DatabaseFailure.KEY_FILE_LOST), state)
        assertTrue(before.contentEquals(path.readBytes()))
        assertTrue(!keyFile.exists())
    }

    @Test
    fun aCorruptKeyFileIsAnErrorAndLeavesTheDatabaseUntouched() = runBlocking {
        open().close()
        val path = context.getDatabasePath(dbName)
        val before = path.readBytes()
        keyFile.writeBytes(keyFile.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
        assertEquals(DatabaseState.Failed(DatabaseFailure.KEY_CORRUPT), EncryptedDatabase.open(context, keyManager, dbName))
        assertTrue(before.contentEquals(path.readBytes()))
    }

    @Test
    fun aDatabaseEncryptedWithAnotherKeyIsReportedUnreadableNotRecreated() = runBlocking {
        open().close()
        val path = context.getDatabasePath(dbName)
        val before = path.readBytes()
        // valid key file for a different database: replace the key with a fresh one
        keyFile.delete()
        val other = DatabaseKeyManager(keyFile, KeystoreSecretWrapper("frostline_instrumented_test_wrap"))
        assertIs<com.vassel.frostline.data.security.DatabaseKeyResult.Ready>(other.open(databaseExists = false))
        assertEquals(DatabaseState.Failed(DatabaseFailure.DATABASE_UNREADABLE), EncryptedDatabase.open(context, keyManager, dbName))
        assertTrue(before.contentEquals(path.readBytes()))
    }

    @Test
    fun triggersAreActiveInTheEncryptedDatabase() = runBlocking {
        val db = open()
        RoomTripRepository(db).create(trip())
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO custody_events (id, trip_id, seq, type, user_id, ts_utc_ms, excursion_open, readings_digest, prev_hash, hash, signature) " +
                "VALUES ('e1', 't1', 1, 'PICKUP', 'op', 1, 0, 'd', 'p', 'h', 's')",
        )
        assertFailsWith<SQLiteException> { db.openHelper.writableDatabase.execSQL("UPDATE custody_events SET user_id = 'x'") }
        assertFailsWith<SQLiteException> { db.openHelper.writableDatabase.execSQL("DELETE FROM custody_events") }
        db.close()
    }
}
