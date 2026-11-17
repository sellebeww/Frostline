// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

enum class KeyFailure {
    /** The key file is gone but an encrypted database exists. A new key would make the data unreadable, so none is made. */
    KEY_FILE_LOST,

    /** The key file exists but cannot be opened (tampered, truncated or sealed by a different key). */
    KEY_CORRUPT,

    /** The wrapping key was removed from the Keystore (for example after a lock screen change or a restore). */
    KEYSTORE_KEY_LOST,

    /** The key file could not be read or written. */
    IO_ERROR,
}

sealed interface DatabaseKeyResult {
    /** The caller owns [passphrase] and must not log it. */
    class Ready(val passphrase: ByteArray) : DatabaseKeyResult
    data class Failed(val reason: KeyFailure) : DatabaseKeyResult
}

/**
 * Owns the SQLCipher passphrase: 32 random bytes, sealed by [wrapper] and stored in [keyFile]. A passphrase is created
 * only when there is neither a key file nor a database. Any other inconsistency is reported, never "fixed" by
 * generating a new key.
 */
class DatabaseKeyManager(
    private val keyFile: File,
    private val wrapper: SecretWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    fun open(databaseExists: Boolean): DatabaseKeyResult {
        return try {
            if (keyFile.exists()) unseal() else if (databaseExists) DatabaseKeyResult.Failed(KeyFailure.KEY_FILE_LOST) else create()
        } catch (_: IOException) {
            DatabaseKeyResult.Failed(KeyFailure.IO_ERROR)
        }
    }

    private fun unseal(): DatabaseKeyResult {
        val bytes = keyFile.readBytes()
        if (bytes.size < 2 || bytes[0] != VERSION) return DatabaseKeyResult.Failed(KeyFailure.KEY_CORRUPT)
        return try {
            val passphrase = wrapper.unwrap(bytes.copyOfRange(1, bytes.size), AAD)
            if (passphrase.size != PASSPHRASE_BYTES) DatabaseKeyResult.Failed(KeyFailure.KEY_CORRUPT) else DatabaseKeyResult.Ready(passphrase)
        } catch (e: SecretException) {
            DatabaseKeyResult.Failed(if (e.kind == SecretException.Kind.KEY_MISSING) KeyFailure.KEYSTORE_KEY_LOST else KeyFailure.KEY_CORRUPT)
        }
    }

    private fun create(): DatabaseKeyResult {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { random.nextBytes(it) }
        val sealed = try {
            wrapper.wrap(passphrase, AAD)
        } catch (e: java.security.GeneralSecurityException) {
            passphrase.fill(0)
            return DatabaseKeyResult.Failed(KeyFailure.KEYSTORE_KEY_LOST)
        }
        writeAtomically(byteArrayOf(VERSION) + sealed)
        return DatabaseKeyResult.Ready(passphrase)
    }

    /** Temp file then atomic rename, so a crash never leaves a half-written key file. */
    private fun writeAtomically(content: ByteArray) {
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(content)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val VERSION: Byte = 1
        const val PASSPHRASE_BYTES = 32
        val AAD = "frostline-db-key-v1".toByteArray(Charsets.UTF_8)
    }
}
