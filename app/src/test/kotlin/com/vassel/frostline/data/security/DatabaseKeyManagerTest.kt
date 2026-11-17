// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.security

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** JVM stand-in for the Keystore wrapper (AndroidKeyStore is not available off-device). */
private class SoftWrapper : SecretWrapper {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    var keyPresent = true

    override fun wrap(plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)); updateAAD(aad) }
        return iv + c.doFinal(plain)
    }

    override fun unwrap(sealed: ByteArray, aad: ByteArray): ByteArray {
        if (!keyPresent) throw SecretException(SecretException.Kind.KEY_MISSING)
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)); updateAAD(aad) }
            c.doFinal(sealed, 12, sealed.size - 12)
        } catch (e: java.security.GeneralSecurityException) {
            throw SecretException(SecretException.Kind.INVALID, e)
        }
    }
}

class DatabaseKeyManagerTest {
    @get:Rule val folder = TemporaryFolder()
    private val wrapper = SoftWrapper()
    private fun keyFile() = File(folder.root, "key.bin")
    private fun manager() = DatabaseKeyManager(keyFile(), wrapper)
    private fun failure(r: DatabaseKeyResult) = assertIs<DatabaseKeyResult.Failed>(r).reason

    @Test
    fun firstOpenCreatesA32ByteKeyThatIsNotStoredInPlain() {
        val ready = assertIs<DatabaseKeyResult.Ready>(manager().open(databaseExists = false))
        assertEquals(32, ready.passphrase.size)
        val onDisk = keyFile().readBytes()
        assertTrue(onDisk.size > 32)
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains(String(ready.passphrase, Charsets.ISO_8859_1)))
        assertFalse(File(folder.root, "key.bin.tmp").exists())
    }

    @Test
    fun reopenReturnsTheSamePassphrase() {
        val first = assertIs<DatabaseKeyResult.Ready>(manager().open(false)).passphrase
        val second = assertIs<DatabaseKeyResult.Ready>(manager().open(true)).passphrase
        assertContentEquals(first, second)
    }

    @Test
    fun differentInstallsGetDifferentPassphrases() {
        val a = assertIs<DatabaseKeyResult.Ready>(manager().open(false)).passphrase
        keyFile().delete()
        val b = assertIs<DatabaseKeyResult.Ready>(manager().open(false)).passphrase
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun lostKeyFileWithAnExistingDatabaseIsAnErrorAndNoNewKeyIsMade() {
        assertEquals(KeyFailure.KEY_FILE_LOST, failure(manager().open(databaseExists = true)))
        assertFalse(keyFile().exists())
    }

    @Test
    fun corruptOrTruncatedKeyFilesAreReportedAndLeftUntouched() {
        manager().open(false)
        val good = keyFile().readBytes()
        val variants = listOf(
            good.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }, // flipped tag bit
            good.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() }, // flipped iv bit
            good.copyOf(good.size - 3), // truncated
            byteArrayOf(1), // header only
            byteArrayOf(), // empty
            good.copyOf().also { it[0] = 9 }, // unknown version
        )
        for (bad in variants) {
            keyFile().writeBytes(bad)
            assertEquals(KeyFailure.KEY_CORRUPT, failure(manager().open(true)))
            assertContentEquals(bad, keyFile().readBytes())
        }
    }

    @Test
    fun aRemovedKeystoreKeyIsReportedAndNotRecreated() {
        manager().open(false)
        val before = keyFile().readBytes()
        wrapper.keyPresent = false
        assertEquals(KeyFailure.KEYSTORE_KEY_LOST, failure(manager().open(true)))
        assertContentEquals(before, keyFile().readBytes())
    }

    @Test
    fun aKeyFileSealedForAnotherPurposeIsRejected() {
        manager().open(false)
        // same bytes in a different file name do not matter, but a different AAD must: seal with a wrong AAD
        keyFile().writeBytes(byteArrayOf(1) + wrapper.wrap(ByteArray(32), "other".toByteArray()))
        assertEquals(KeyFailure.KEY_CORRUPT, failure(manager().open(true)))
    }

    @Test
    fun aWrongLengthPassphraseInsideAValidSealIsRejected() {
        keyFile().writeBytes(byteArrayOf(1) + wrapper.wrap(ByteArray(16), "frostline-db-key-v1".toByteArray()))
        assertEquals(KeyFailure.KEY_CORRUPT, failure(manager().open(true)))
    }
}
