// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vassel.frostline.R
import androidx.compose.foundation.layout.heightIn
import com.vassel.frostline.ui.components.MinTouch
import com.vassel.frostline.ui.components.PrimaryButton

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val steps = remember { OnboardingSteps.forSdk(Build.VERSION.SDK_INT) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("") }
    val step = steps[index]
    val next = { if (index < steps.lastIndex) index++ }
    val permissions = OnboardingSteps.permissions(step, Build.VERSION.SDK_INT)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }
    fun granted() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    // A permission that is already granted needs no step.
    LaunchedEffect(step) { if (permissions.isNotEmpty() && granted()) next() }
    val defaultRole = stringResource(R.string.operator_role_default)

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(200))) },
            label = "onboarding",
        ) { s ->
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(titleOf(s)), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(bodyOf(s)), style = MaterialTheme.typography.bodyLarge)
                if (s == OnboardingStep.OPERATOR) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.operator_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        role, { role = it }, label = { Text(stringResource(R.string.operator_role)) },
                        placeholder = { Text(defaultRole) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        when {
            permissions.isNotEmpty() -> {
                PrimaryButton(stringResource(R.string.onboarding_allow), { launcher.launch(permissions.toTypedArray()) })
                TextButton(onClick = next, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.onboarding_skip)) }
            }
            step == OnboardingStep.OPERATOR -> PrimaryButton(stringResource(R.string.operator_save), { vm.saveOperator(name, role.ifBlank { defaultRole }); next() }, enabled = name.isNotBlank())
            step == OnboardingStep.DISCLAIMER -> PrimaryButton(stringResource(R.string.onboarding_finish), { vm.finish(); onFinished() })
            else -> PrimaryButton(stringResource(R.string.onboarding_next), next)
        }
    }
}

private fun titleOf(s: OnboardingStep) = when (s) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
    OnboardingStep.BLUETOOTH -> R.string.onboarding_bluetooth_title
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
    OnboardingStep.LOCATION -> R.string.onboarding_location_title
    OnboardingStep.OPERATOR -> R.string.operator_title
    OnboardingStep.DISCLAIMER -> R.string.onboarding_disclaimer_title
}

private fun bodyOf(s: OnboardingStep) = when (s) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_body
    OnboardingStep.BLUETOOTH -> R.string.onboarding_bluetooth_body
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_body
    OnboardingStep.LOCATION -> R.string.onboarding_location_body
    OnboardingStep.OPERATOR -> R.string.operator_hint
    OnboardingStep.DISCLAIMER -> R.string.home_disclaimer
}
