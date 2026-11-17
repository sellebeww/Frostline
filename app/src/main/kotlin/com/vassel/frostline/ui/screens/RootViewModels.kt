// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import androidx.lifecycle.ViewModel
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.data.OperatorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val operators: OperatorStore,
    private val settings: AppSettings,
) : ViewModel() {
    fun saveOperator(name: String, role: String) {
        operators.save(name, role)
    }

    fun finish() {
        settings.onboardingDone = true
    }
}

/** Decides where the app starts. */
@HiltViewModel
class RootViewModel @Inject constructor(settings: AppSettings) : ViewModel() {
    val onboardingDone: Boolean = settings.onboardingDone
}
