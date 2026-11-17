// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.nfc

import android.app.Activity
import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class NfcAvailability { AVAILABLE, DISABLED, NOT_SUPPORTED }

sealed interface NfcTagEvent {
    data class Frostline(val info: TagInfo, val uidHash: String) : NfcTagEvent
    data class Foreign(val reason: ForeignReason, val uidHash: String) : NfcTagEvent
}

internal const val READER_FLAGS =
    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V

internal fun NdefMessage.firstFrostlineUri(): String? =
    records.firstNotNullOfOrNull { r -> runCatching { r.toUri()?.toString() }.getOrNull()?.takeIf { it.startsWith(NdefCodec.PREFIX, ignoreCase = true) } }

@Singleton
class NfcReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun availability(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.NOT_SUPPORTED
        return if (adapter.isEnabled) NfcAvailability.AVAILABLE else NfcAvailability.DISABLED
    }

    /**
     * Reader mode exists only while this flow is collected, so collect it while the screen is resumed and cancel it
     * on pause. NDEF checking is left on (FLAG_READER_SKIP_NDEF_CHECK is not used) so that NDEF content is readable.
     */
    fun tags(activity: Activity): Flow<NfcTagEvent> = callbackFlow {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }
        adapter.enableReaderMode(activity, { tag -> trySend(read(tag)) }, READER_FLAGS, null)
        awaitClose { runCatching { adapter.disableReaderMode(activity) } }
    }

    internal fun read(tag: Tag): NfcTagEvent {
        val uidHash = TagUid.hash(tag.id)
        val ndef = Ndef.get(tag) ?: return NfcTagEvent.Foreign(ForeignReason.EMPTY, uidHash) // blank or not NDEF
        val uri = try {
            ndef.connect()
            try {
                (ndef.ndefMessage ?: ndef.cachedNdefMessage)?.firstFrostlineUri()
            } finally {
                runCatching { ndef.close() }
            }
        } catch (_: IOException) {
            return NfcTagEvent.Foreign(ForeignReason.UNREADABLE, uidHash)
        } catch (_: FormatException) {
            return NfcTagEvent.Foreign(ForeignReason.UNREADABLE, uidHash)
        } catch (_: SecurityException) {
            return NfcTagEvent.Foreign(ForeignReason.UNREADABLE, uidHash)
        }
        return when (val d = NdefCodec.decode(uri)) {
            is TagDecode.Valid -> NfcTagEvent.Frostline(d.info, uidHash)
            is TagDecode.Foreign -> NfcTagEvent.Foreign(if (uri == null) ForeignReason.NOT_FROSTLINE else d.reason, uidHash)
        }
    }
}
