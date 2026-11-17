// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.port.PublicKeySource
import com.vassel.frostline.domain.port.Signer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** ECDSA P-256 signer with a non-exportable key in the Android Keystore (StrongBox when available, else TEE). */
@Singleton
class KeystoreSigner @Inject constructor() : Signer, PublicKeySource {
    private val lock = Any()

    /** DER encoded SHA256withECDSA signature of [data]. */
    override fun sign(data: ByteArray): ByteArray {
        val key = privateKey()
        return Signature.getInstance(ALGORITHM).run {
            initSign(key)
            update(data)
            sign()
        }
    }

    override fun publicKey(): PublicKey = synchronized(lock) {
        ensureKey()
        keyStore().getCertificate(ALIAS).publicKey
    }

    fun publicKeyX509Base64(): String = Base64.getEncoder().encodeToString(publicKey().encoded)

    /** SHA-256 over the X.509 encoded public key, lowercase hex. */
    fun keyFingerprint(): String = Hex.sha256(publicKey().encoded)

    private fun privateKey(): PrivateKey = synchronized(lock) {
        ensureKey()
        checkNotNull(keyStore().getKey(ALIAS, null) as? PrivateKey) { "Signing key unavailable" }
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun ensureKey() {
        if (keyStore().containsAlias(ALIAS)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(strongBox = true)
                return
            } catch (_: StrongBoxUnavailableException) {
                // fall through to the TEE-backed key
            } catch (_: ProviderException) {
                // some devices report a missing StrongBox as a plain ProviderException
            }
        }
        generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply { initialize(builder.build()) }.generateKeyPair()
    }

    companion object {
        const val ALIAS = "frostline_signing_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "SHA256withECDSA"
    }
}
