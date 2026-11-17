// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Shares report files through the system share sheet with read-only URI grants (FileProvider, no public storage). */
object ReportSharer {
    fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun uris(context: Context, files: List<File>): List<Uri> = files.map { FileProvider.getUriForFile(context, authority(context), it) }

    fun shareIntent(context: Context, files: List<File>, title: String): Intent {
        val uris = ArrayList(uris(context, files))
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }
}
