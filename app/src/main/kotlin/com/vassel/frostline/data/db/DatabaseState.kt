// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import com.vassel.frostline.data.security.DatabaseKeyManager
import com.vassel.frostline.data.security.DatabaseKeyResult
import com.vassel.frostline.data.security.KeyFailure
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DatabaseUnavailableException(val failure: DatabaseFailure) : IllegalStateException("Database unavailable: $failure")

enum class DatabaseFailure {
    KEY_FILE_LOST, KEY_CORRUPT, KEYSTORE_KEY_LOST, IO_ERROR,

    /** The key opened but the database file could not be decrypted or read. It is left untouched. */
    DATABASE_UNREADABLE,
}

/** Either an open encrypted database or an explicit reason why there is none. There is no plaintext fallback. */
sealed interface DatabaseState {
    class Ready(val database: FrostlineDatabase) : DatabaseState
    data class Failed(val failure: DatabaseFailure) : DatabaseState
}

object EncryptedDatabase {
    /** Blocking (key unsealing, file I/O, first open): call off the main thread. */
    fun open(context: Context, keyManager: DatabaseKeyManager, fileName: String = FrostlineDatabase.FILE_NAME): DatabaseState {
        val exists = context.getDatabasePath(fileName).exists()
        val passphrase = when (val key = keyManager.open(exists)) {
            is DatabaseKeyResult.Ready -> key.passphrase
            is DatabaseKeyResult.Failed -> return DatabaseState.Failed(key.reason.toFailure())
        }
        System.loadLibrary("sqlcipher")
        val db = FrostlineDatabase.create(context, fileName, SupportOpenHelperFactory(passphrase, null, false))
        return try {
            db.openHelper.writableDatabase // force the open so a wrong key surfaces here, not later
            DatabaseState.Ready(db)
        } catch (_: SQLiteException) {
            db.close()
            DatabaseState.Failed(DatabaseFailure.DATABASE_UNREADABLE)
        }
    }

    private fun KeyFailure.toFailure() = when (this) {
        KeyFailure.KEY_FILE_LOST -> DatabaseFailure.KEY_FILE_LOST
        KeyFailure.KEY_CORRUPT -> DatabaseFailure.KEY_CORRUPT
        KeyFailure.KEYSTORE_KEY_LOST -> DatabaseFailure.KEYSTORE_KEY_LOST
        KeyFailure.IO_ERROR -> DatabaseFailure.IO_ERROR
    }
}
