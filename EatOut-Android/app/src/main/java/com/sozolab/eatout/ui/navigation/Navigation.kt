package com.sozolab.eatout.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.sozolab.eatout.ui.auth.AuthScreen
import com.sozolab.eatout.ui.auth.AuthViewModel
import com.sozolab.eatout.ui.main.MainScreen
import com.sozolab.eatout.ui.merchant.MerchantProfileSetupScreen
import com.sozolab.eatout.ui.merchant.MerchantProfileSetupViewModel
import com.sozolab.eatout.ui.onboarding.LocationOnboardingScreen

sealed class Route(val route: String) {
    data object Auth : Route("auth")
    data object Main : Route("main")
    data object MerchantSetup : Route("merchant_setup")
    data object LocationOnboarding : Route("location_onboarding")
    data object Stats : Route("stats")
    data object MenuDetail : Route("menu_detail/{menuId}") {
        fun createRoute(menuId: String) = "menu_detail/$menuId"
    }
}

@Composable
fun EatOutNavHost(startMenuId: String? = null) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

    val startDestination = if (isAuthenticated) Route.Main.route else Route.Auth.route

    LaunchedEffect(isAuthenticated, startMenuId) {
        if (isAuthenticated && !startMenuId.isNullOrBlank()) {
            navController.navigate(Route.MenuDetail.createRoute(startMenuId))
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.Main.route) {
            MainScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Auth.route) {
                        popUpTo(Route.Main.route) { inclusive = true }
                    }
                },
                onNavigateToSetup = {
                    navController.navigate(Route.MerchantSetup.route)
                },
                onNavigateToLocation = {
                    navController.navigate(Route.LocationOnboarding.route)
                },
                onNavigateToDetail = { menuId ->
                    navController.navigate(Route.MenuDetail.createRoute(menuId))
                },
                onNavigateToStats = {
                    navController.navigate(Route.Stats.route)
                }
            )
        }
        composable(Route.MerchantSetup.route) {
            val setupViewModel: MerchantProfileSetupViewModel = hiltViewModel()
            MerchantProfileSetupScreen(
                viewModel = setupViewModel,
                onSuccess = {
                    navController.popBackStack()
                },
                onSkip = {
                    navController.popBackStack()
                }
            )
        }
        composable(Route.LocationOnboarding.route) {
            com.sozolab.eatout.ui.onboarding.LocationOnboardingScreen(
                onPermissionGranted = {
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
        composable(Route.Stats.route) {
            com.sozolab.eatout.ui.stats.StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Route.MenuDetail.route,
            arguments = listOf(navArgument("menuId") { type = NavType.StringType })
        ) { backStackEntry ->
            val menuId = backStackEntry.arguments?.getString("menuId") ?: ""
            com.sozolab.eatout.ui.feed.MenuDetailScreen(
                menuId = menuId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
