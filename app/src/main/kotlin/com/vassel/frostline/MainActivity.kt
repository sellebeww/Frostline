// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vassel.frostline.ui.navigation.DatabaseGate
import com.vassel.frostline.ui.navigation.FrostlineScaffold
import com.vassel.frostline.ui.theme.FrostlineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrostlineTheme {
                DatabaseGate { FrostlineScaffold() }
            }
        }
    }
}
