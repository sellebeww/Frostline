// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { KEY_MISSING, INVALID }
}

/** Seals small secrets with a key that never leaves secure storage. */
interface SecretWrapper {
    /** Returns iv followed by ciphertext and tag. Creates the wrapping key if it does not exist yet. */
    fun wrap(plain: ByteArray, aad: ByteArray): ByteArray

    /** Never creates a key: a missing key is [SecretException.Kind.KEY_MISSING], a failed check is INVALID. */
    fun unwrap(sealed: ByteArray, aad: ByteArray): ByteArray
}

/** AES-256-GCM with a non-exportable Android Keystore key. */
class KeystoreSecretWrapper(private val alias: String) : SecretWrapper {
    override fun wrap(plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore generates the IV itself (randomized encryption is required).
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw SecretException(SecretException.Kind.INVALID)
        val key = existingKey() ?: throw SecretException(SecretException.Kind.KEY_MISSING)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw SecretException(SecretException.Kind.INVALID, e)
        }
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
