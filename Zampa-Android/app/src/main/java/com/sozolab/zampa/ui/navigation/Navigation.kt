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
    data object Subscription : Route("subscription")
    data object DietaryPreferences : Route("dietary_preferences")
    data object NotificationPreferences : Route("notification_preferences")
    data object CurrencyPreference : Route("currency_preference")
    data object History : Route("history")
    data object Language : Route("language")
    data object PrivacyPolicy : Route("privacy_policy")
    data object Terms : Route("terms")
    data object MenuDetail : Route("menu_detail/{menuId}") {
        fun createRoute(menuId: String) = "menu_detail/$menuId"
    }
    data object MerchantProfile : Route("merchant_profile/{merchantId}") {
        fun createRoute(merchantId: String) = "merchant_profile/$merchantId"
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
    //  - Se elimina la cuenta (pendingDeletion null, isAuthenticated flipa a false,
    //    no estamos en Auth) → Auth. Para cubrir el caso de solicitar eliminación
    //    desde Profile y que la app vuelva al login.
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
            pendingDeletionUser == null && !isAuthenticated && current != null && current != Route.Auth.route && current != Route.PrivacyPolicy.route && current != Route.Terms.route -> {
                navController.navigate(Route.Auth.route) {
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
                onNavigateToMerchant = { merchantId ->
                    navController.navigate(Route.MerchantProfile.createRoute(merchantId))
                },
                onNavigateToStats = {
                    navController.navigate(Route.Stats.route)
                },
                onNavigateToSubscription = {
                    navController.navigate(Route.Subscription.route)
                },
                onNavigateToDietaryPreferences = {
                    navController.navigate(Route.DietaryPreferences.route)
                },
                onNavigateToNotificationPreferences = {
                    navController.navigate(Route.NotificationPreferences.route)
                },
                onNavigateToCurrencyPreference = {
                    navController.navigate(Route.CurrencyPreference.route)
                },
                onNavigateToLanguage = {
                    navController.navigate(Route.Language.route)
                },
                onNavigateToLegal = {
                    navController.navigate(Route.PrivacyPolicy.route)
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
        composable(Route.Subscription.route) {
            com.sozolab.zampa.ui.subscription.SubscriptionScreen(
                onDismiss = { navController.popBackStack() }
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
        composable(Route.CurrencyPreference.route) {
            val user by authViewModel.currentUser.collectAsState()
            com.sozolab.zampa.ui.profile.CurrencyPreferenceScreen(
                currentCode = user?.currencyPreference ?: "EUR",
                onSelect = { code, onError -> authViewModel.updateCurrencyPreference(code, onError) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.Language.route) {
            val user by authViewModel.currentUser.collectAsState()
            com.sozolab.zampa.ui.profile.LanguagePickerScreen(
                localizationManager = authViewModel.localizationManager,
                userId = user?.id,
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
                onBack = { navController.popBackStack() },
                onNavigateToMerchant = { merchantId ->
                    navController.navigate(Route.MerchantProfile.createRoute(merchantId))
                }
            )
        }
        composable(
            Route.MerchantProfile.route,
            arguments = listOf(navArgument("merchantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val merchantId = backStackEntry.arguments?.getString("merchantId") ?: ""
            com.sozolab.zampa.ui.feed.MerchantProfileScreen(
                merchantId = merchantId,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { menuId ->
                    navController.navigate(Route.MenuDetail.createRoute(menuId))
                }
            )
        }
    }
}
