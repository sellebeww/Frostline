// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vassel.frostline.ui.screens.BatteryGuideScreen
import com.vassel.frostline.ui.screens.HandoverScreen
import com.vassel.frostline.ui.screens.HomeScreen
import com.vassel.frostline.ui.screens.OnboardingScreen
import com.vassel.frostline.ui.screens.RootViewModel
import com.vassel.frostline.ui.screens.ScanScreen
import com.vassel.frostline.ui.screens.SettingsScreen
import com.vassel.frostline.ui.screens.StartTripScreen
import com.vassel.frostline.ui.screens.TripDetailScreen
import com.vassel.frostline.ui.screens.TripListScreen
import com.vassel.frostline.ui.screens.WriteTagScreen

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_SCAN = "scan"
private const val ROUTE_BATTERY = "battery"
private const val ROUTE_HANDOVER = "handover"
private const val ROUTE_START_TRIP = "start_trip"
private const val ROUTE_TRIP = "trip/{tripId}"

@Composable
fun FrostlineScaffold(root: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val current = navController.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        bottomBar = {
            if (current != ROUTE_ONBOARDING) {
                NavigationBar {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Destination.Home.route)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = if (root.onboardingDone) Destination.Home.route else ROUTE_ONBOARDING,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Destination.Home.route) { popUpTo(ROUTE_ONBOARDING) { inclusive = true } }
                })
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    onStartTrip = { navController.navigate(ROUTE_START_TRIP) },
                    onPickSensor = { navController.navigate(ROUTE_SCAN) },
                    onBatteryGuide = { navController.navigate(ROUTE_BATTERY) },
                    onHandover = { navController.navigate(ROUTE_HANDOVER) },
                )
            }
            composable(ROUTE_START_TRIP) {
                StartTripScreen(
                    onPickSensor = { navController.navigate(ROUTE_SCAN) },
                    onStarted = { navController.popBackStack(Destination.Home.route, inclusive = false) },
                    onSettings = { navController.navigate(Destination.Settings.route) },
                )
            }
            composable(Destination.Trips.route) { TripListScreen(onOpen = { navController.navigate("trip/$it") }) }
            composable(ROUTE_TRIP, arguments = listOf(navArgument("tripId") { type = NavType.StringType })) { TripDetailScreen() }
            composable(Destination.WriteTag.route) { WriteTagScreen() }
            composable(Destination.Settings.route) { SettingsScreen(onBatteryGuide = { navController.navigate(ROUTE_BATTERY) }) }
            composable(ROUTE_HANDOVER) { HandoverScreen() }
            composable(ROUTE_SCAN) { ScanScreen(onDone = { navController.popBackStack() }) }
            composable(ROUTE_BATTERY) { BatteryGuideScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
