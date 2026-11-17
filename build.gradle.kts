// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

allprojects {
    group = "com.vassel.frostline"
    version = "0.1.0"
    description = "Frostline: vaccine cold chain monitor. Owner: Vassel Goleyu (MIT)."
}
