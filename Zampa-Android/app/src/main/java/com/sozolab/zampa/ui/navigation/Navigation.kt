package com.sozolab.zampa.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.sozolab.zampa.ui.auth.AccountDeletionRecoveryScreen
import com.sozolab.zampa.ui.auth.AuthScreen
import com.sozolab.zampa.ui.auth.AuthViewModel
import com.sozolab.zampa.ui.legal.LegalScreen
import com.sozolab.zampa.ui.legal.LegalType
import com.sozolab.zampa.ui.main.MainScreen
import com.sozolab.zampa.ui.merchant.MerchantProfileSetupScreen
import com.sozolab.zampa.ui.merchant.MerchantProfileSetupViewModel
import com.sozolab.zampa.ui.onboarding.LocationOnboardingScreen

sealed class Route(val route: String) {
    data object Auth : Route("auth")
    data object Main : Route("main")
    data object AccountDeletionRecovery : Route("account_deletion_recovery")
    data object MerchantSetup : Route("merchant_setup")
    data object LocationOnboarding : Route("location_onboarding")
    data object Stats : Route("stats")
    data object DietaryPreferences : Route("dietary_preferences")
    data object NotificationPreferences : Route("notification_preferences")
    data object History : Route("history")
    data object PrivacyPolicy : Route("privacy_policy")
    data object Terms : Route("terms")
    data object MenuDetail : Route("menu_detail/{menuId}") {
        fun createRoute(menuId: String) = "menu_detail/$menuId"
    }
}

@Composable
fun ZampaNavHost(
    startMenuId: String? = null,
    onMenuConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val pendingDeletionUser by authViewModel.pendingDeletionUser.collectAsState()

    val startDestination = when {
        pendingDeletionUser != null -> Route.AccountDeletionRecovery.route
        isAuthenticated -> Route.Main.route
        else -> Route.Auth.route
    }

    // Reaccionar a cambios runtime:
    //  - Aparece pendingDeletionUser → ir a pantalla de recuperación
    //  - Se recupera la cuenta (pendingDeletion pasa a null, isAuthenticated true) → Main
    LaunchedEffect(pendingDeletionUser, isAuthenticated) {
        val current = navController.currentDestination?.route
        when {
            pendingDeletionUser != null && current != Route.AccountDeletionRecovery.route -> {
                navController.navigate(Route.AccountDeletionRecovery.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            pendingDeletionUser == null && isAuthenticated && current == Route.AccountDeletionRecovery.route -> {
                navController.navigate(Route.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Deep link → abre la oferta. Las dailyOffers son lectura pública por rules,
    // así que también funciona si el usuario aún no ha iniciado sesión.
    LaunchedEffect(startMenuId) {
        if (!startMenuId.isNullOrBlank()) {
            navController.navigate(Route.MenuDetail.createRoute(startMenuId))
            onMenuConsumed()
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
                },
                onNavigateToPrivacyPolicy = { navController.navigate(Route.PrivacyPolicy.route) },
                onNavigateToTerms = { navController.navigate(Route.Terms.route) }
            )
        }
        composable(Route.AccountDeletionRecovery.route) {
            val pending = pendingDeletionUser
            if (pending != null) {
                AccountDeletionRecoveryScreen(
                    user = pending,
                    onRecover = { onError ->
                        authViewModel.cancelAccountDeletion(onError)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Route.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
        composable(Route.PrivacyPolicy.route) {
            LegalScreen(type = LegalType.PRIVACY_POLICY, onBack = { navController.popBackStack() })
        }
        composable(Route.Terms.route) {
            LegalScreen(type = LegalType.TERMS, onBack = { navController.popBackStack() })
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
                },
                onNavigateToDietaryPreferences = {
                    navController.navigate(Route.DietaryPreferences.route)
                },
                onNavigateToNotificationPreferences = {
                    navController.navigate(Route.NotificationPreferences.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Route.History.route)
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
            com.sozolab.zampa.ui.onboarding.LocationOnboardingScreen(
                onPermissionGranted = {
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
        composable(Route.Stats.route) {
            com.sozolab.zampa.ui.stats.StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.DietaryPreferences.route) {
            com.sozolab.zampa.ui.profile.DietaryPreferencesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.NotificationPreferences.route) {
            com.sozolab.zampa.ui.profile.NotificationPreferencesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.History.route) {
            com.sozolab.zampa.ui.profile.HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Route.MenuDetail.route,
            arguments = listOf(navArgument("menuId") { type = NavType.StringType })
        ) { backStackEntry ->
            val menuId = backStackEntry.arguments?.getString("menuId") ?: ""
            com.sozolab.zampa.ui.feed.MenuDetailScreen(
                menuId = menuId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
