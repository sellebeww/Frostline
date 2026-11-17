// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.vassel.frostline.R

enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Filled.Home),
    Trips("trips", R.string.nav_trips, Icons.AutoMirrored.Filled.List),
    WriteTag("write_tag", R.string.nav_write_tag, Icons.Filled.Edit),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}
