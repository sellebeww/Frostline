// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.nfc

import android.app.Activity
import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class WriteResult { SUCCESS, READ_ONLY, TOO_SMALL, UNSUPPORTED, VERIFY_FAILED, IO_ERROR, NFC_UNAVAILABLE }

@Singleton
class NfcWriter @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Waits for one tag, writes [uri] as a single NDEF URI record and reads it back to confirm. Cancelling the
     * coroutine turns reader mode off. A tag that is read-only or too small is reported, not retried.
     */
    suspend fun write(activity: Activity, uri: String): WriteResult = suspendCancellableCoroutine { cont ->
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null || !adapter.isEnabled) {
            cont.resume(WriteResult.NFC_UNAVAILABLE)
            return@suspendCancellableCoroutine
        }
        val done = AtomicBoolean(false)
        adapter.enableReaderMode(activity, { tag ->
            if (done.compareAndSet(false, true)) {
                val result = writeTag(tag, uri)
                runCatching { adapter.disableReaderMode(activity) }
                if (cont.isActive) cont.resume(result)
            }
        }, READER_FLAGS, null)
        cont.invokeOnCancellation { runCatching { adapter.disableReaderMode(activity) } }
    }

    private fun writeTag(tag: Tag, uri: String): WriteResult {
        val message = NdefMessage(NdefRecord.createUri(uri))
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                writeNdef(ndef, message)
            } else {
                val formatable = NdefFormatable.get(tag) ?: return WriteResult.UNSUPPORTED
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    runCatching { formatable.close() }
                }
            }
            if (verify(tag, uri)) WriteResult.SUCCESS else WriteResult.VERIFY_FAILED
        } catch (e: TooSmall) {
            WriteResult.TOO_SMALL
        } catch (e: ReadOnly) {
            WriteResult.READ_ONLY
        } catch (_: IOException) {
            WriteResult.IO_ERROR // includes TagLostException
        } catch (_: FormatException) {
            WriteResult.IO_ERROR
        }
    }

    private fun writeNdef(ndef: Ndef, message: NdefMessage) {
        ndef.connect()
        try {
            if (!ndef.isWritable) throw ReadOnly()
            if (ndef.maxSize < message.byteArrayLength) throw TooSmall()
            ndef.writeNdefMessage(message)
        } finally {
            runCatching { ndef.close() }
        }
    }

    /** Reads the tag again and compares the decoded id and label with what was meant to be written. */
    private fun verify(tag: Tag, uri: String): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            try {
                val read = (ndef.ndefMessage ?: return false).firstFrostlineUri()
                val expected = NdefCodec.decode(uri)
                expected is TagDecode.Valid && NdefCodec.decode(read) == expected
            } finally {
                runCatching { ndef.close() }
            }
        } catch (_: IOException) {
            false
        } catch (_: FormatException) {
            false
        }
    }

    private class TooSmall : Exception()
    private class ReadOnly : Exception()
}
