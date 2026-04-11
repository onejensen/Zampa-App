package com.sozolab.zampa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sozolab.zampa.ui.auth.AuthViewModel
import com.sozolab.zampa.ui.feed.FeedScreen
import com.sozolab.zampa.ui.merchant.DashboardScreen
import com.sozolab.zampa.ui.onboarding.AppOnboardingScreen
import com.sozolab.zampa.data.model.User

enum class Tab(val label: String) {
    FEED("Feed"), FAVORITES("Favoritos"), DASHBOARD("Mis Menús"), PROFILE("Perfil")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eatout_prefs", android.content.Context.MODE_PRIVATE) }

    val currentUser by authViewModel.currentUser.collectAsState()
    val pendingPhotoBitmap by authViewModel.pendingPhotoBitmap.collectAsState()
    val needsSetup by mainViewModel.needsSetup.collectAsState()
    val needsLocationPrompt by mainViewModel.needsLocationPrompt.collectAsState()
    val isMerchant = currentUser?.role == User.UserRole.COMERCIO
    var showOnboarding by remember { mutableStateOf(false) }
    var onboardingChecked by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(Tab.FEED) }

    LaunchedEffect(currentUser) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        if (!onboardingChecked) {
            onboardingChecked = true
            showOnboarding = !prefs.getBoolean("hasSeenOnboarding_$uid", false)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.checkLocationPermission(context)
    }

    LaunchedEffect(needsSetup) {
        if (needsSetup) {
            onNavigateToSetup()
            mainViewModel.dismissSetup()
        }
    }

    LaunchedEffect(needsLocationPrompt) {
        if (needsLocationPrompt) {
            onNavigateToLocation()
            mainViewModel.dismissLocation()
        }
    }

    if (showOnboarding) {
        AppOnboardingScreen(
            isMerchant = isMerchant,
            onFinish = {
                val uid = currentUser?.id
                if (uid != null) prefs.edit().putBoolean("hasSeenOnboarding_$uid", true).apply()
                showOnboarding = false
            }
        )
        return
    }

    val tabs = if (isMerchant) listOf(Tab.FEED, Tab.FAVORITES, Tab.DASHBOARD, Tab.PROFILE)
    else listOf(Tab.FEED, Tab.FAVORITES, Tab.PROFILE)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.FEED -> Icons.Default.Home
                                    Tab.FAVORITES -> Icons.Default.Favorite
                                    Tab.DASHBOARD -> Icons.Default.AddBox
                                    Tab.PROFILE -> Icons.Default.Person
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            Tab.FEED -> {
                FeedScreen(
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToProfile = { selectedTab = Tab.PROFILE },
                    onNavigateToLocation = onNavigateToLocation,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            Tab.FAVORITES -> {
                com.sozolab.zampa.ui.favorites.FavoritesScreen(
                    onNavigateToDetail = onNavigateToDetail
                )
            }
            Tab.DASHBOARD -> {
                DashboardScreen(
                    modifier = Modifier.padding(paddingValues)
                )
            }
            Tab.PROFILE -> {
                com.sozolab.zampa.ui.profile.ProfileScreen(
                    user = currentUser,
                    pendingPhotoBitmap = pendingPhotoBitmap,
                    isMerchant = isMerchant,
                    onLogout = onLogout,
                    onUserNameUpdated = { name -> authViewModel.updateUserName(name) },
                    onProfilePhotoUpdated = { bitmap, photoData -> authViewModel.updateProfilePhoto(bitmap, photoData) },
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToEditProfile = onNavigateToSetup,
                    onNavigateToSubscription = {},
                    onNavigateToDietaryPreferences = onNavigateToDietaryPreferences,
                    onNavigateToNotificationPreferences = onNavigateToNotificationPreferences,
                    onNavigateToHistory = onNavigateToHistory,
                    onRequestAccountDeletion = { onError -> authViewModel.requestAccountDeletion(onError) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun ProfileSection(user: User?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(user?.name ?: "Usuario", style = MaterialTheme.typography.headlineMedium)
        Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            if (user?.role == User.UserRole.COMERCIO) "Restaurante" else "Cliente",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}
