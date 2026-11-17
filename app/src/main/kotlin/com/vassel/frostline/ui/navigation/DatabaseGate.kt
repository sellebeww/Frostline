// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vassel.frostline.R
import com.vassel.frostline.data.db.DatabaseFailure
import com.vassel.frostline.data.db.DatabaseState
import com.vassel.frostline.di.DataEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the encrypted database off the main thread. On failure it shows why and never touches the data. */
@Composable
fun DatabaseGate(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val state by produceState<DatabaseState?>(null) {
        value = withContext(Dispatchers.IO) { EntryPointAccessors.fromApplication(context, DataEntryPoint::class.java).databaseState() }
    }
    when (val s = state) {
        null -> Centered { Text(stringResource(R.string.db_loading)) }
        is DatabaseState.Failed -> Centered {
            Text(stringResource(R.string.db_error_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(failureText(s.failure)))
            Text(stringResource(R.string.db_error_note), style = MaterialTheme.typography.bodySmall)
        }
        is DatabaseState.Ready -> content()
    }
}

@Composable
private fun Centered(body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { body() }
}

private fun failureText(f: DatabaseFailure) = when (f) {
    DatabaseFailure.KEY_FILE_LOST -> R.string.db_error_key_file_lost
    DatabaseFailure.KEY_CORRUPT -> R.string.db_error_key_corrupt
    DatabaseFailure.KEYSTORE_KEY_LOST -> R.string.db_error_keystore_key_lost
    DatabaseFailure.IO_ERROR -> R.string.db_error_io
    DatabaseFailure.DATABASE_UNREADABLE -> R.string.db_error_unreadable
}
