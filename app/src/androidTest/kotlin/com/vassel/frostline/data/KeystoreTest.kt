// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vassel.frostline.data.security.KeystoreSecretWrapper
import com.vassel.frostline.data.security.KeystoreSigner
import com.vassel.frostline.data.security.SecretException
import com.vassel.frostline.domain.integrity.ChainVerification
import com.vassel.frostline.domain.integrity.ChainVerifier
import com.vassel.frostline.domain.integrity.Hex
import com.vassel.frostline.domain.integrity.HashChain
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyFields
import com.vassel.frostline.domain.model.CustodyType
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.Signature
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KeystoreTest {
    private val signer = KeystoreSigner()

    private fun chain(): List<CustodyEvent> {
        val types = listOf(CustodyType.PICKUP, CustodyType.HANDOFF, CustodyType.DELIVERY)
        val out = mutableListOf<CustodyEvent>()
        types.forEachIndexed { i, t ->
            val f = CustodyFields("trip-ks", t, "tag$i", "Tag $i", 1_000L + i, -6.2, 106.8, 5.0, "op", 500, false, "d".repeat(64))
            out += HashChain.appendEvent(out.lastOrNull(), f, signer)
        }
        return out
    }

    @Test
    fun keystoreSignaturesVerifyWithTheDomainChainVerifier() {
        assertEquals(ChainVerification.Valid, ChainVerifier.verify(chain(), signer.publicKey()))
    }

    @Test
    fun aTamperedKeystoreSignedChainIsDetected() {
        val c = chain()
        val bad = c.toMutableList().also { it[1] = it[1].copy(userId = "mallory") }
        assertIs<ChainVerification.Invalid>(ChainVerifier.verify(bad, signer.publicKey()))
    }

    @Test
    fun signsArbitraryBytesAndExposesPublicKeyAndFingerprint() {
        val data = "report bytes".toByteArray()
        val sig = signer.sign(data)
        val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(signer.publicKey()); update(data); verify(sig) }
        assertTrue(ok)
        val der = Base64.getDecoder().decode(signer.publicKeyX509Base64())
        assertContentEquals(signer.publicKey().encoded, der)
        assertEquals(Hex.sha256(der), signer.keyFingerprint())
        assertEquals(64, signer.keyFingerprint().length)
    }

    @Test
    fun theSameKeyIsUsedByNewInstancesAndThePrivateKeyIsNotExportable() {
        assertEquals(signer.keyFingerprint(), KeystoreSigner().keyFingerprint())
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(ks.getKey(KeystoreSigner.ALIAS, null).encoded)
    }

    @Test
    fun wrapperRoundTripsAndNeverRecreatesAMissingKeyOnUnwrap() {
        val alias = "frostline_instrumented_wrapper_test"
        val w = KeystoreSecretWrapper(alias)
        val secret = ByteArray(32) { it.toByte() }
        val aad = "aad".toByteArray()
        val sealed = w.wrap(secret, aad)
        assertContentEquals(secret, w.unwrap(sealed, aad))
        assertEquals(SecretException.Kind.INVALID, assertFailsWith<SecretException> { w.unwrap(sealed, "other".toByteArray()) }.kind)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertEquals(SecretException.Kind.KEY_MISSING, assertFailsWith<SecretException> { w.unwrap(sealed, aad) }.kind)
        assertEquals(false, KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias))
    }
}
