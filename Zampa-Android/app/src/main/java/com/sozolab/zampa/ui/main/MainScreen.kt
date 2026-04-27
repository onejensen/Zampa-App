package com.sozolab.zampa.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sozolab.zampa.R
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.sozolab.zampa.ui.auth.AuthViewModel
import com.sozolab.zampa.ui.feed.FeedScreen
import com.sozolab.zampa.ui.merchant.DashboardScreen
import com.sozolab.zampa.ui.onboarding.AppOnboardingScreen
import com.sozolab.zampa.data.model.User
import com.sozolab.zampa.ui.tour.TourBounds
import com.sozolab.zampa.ui.tour.TourOverlay
import com.sozolab.zampa.ui.tour.TourTarget
import com.sozolab.zampa.ui.tour.TourViewModel

enum class Tab {
    FEED, FAVORITES, DASHBOARD, PROFILE
}

@Composable
fun Tab.label(): String = when (this) {
    Tab.FEED -> stringResource(R.string.tab_feed)
    Tab.FAVORITES -> stringResource(R.string.tab_favorites)
    Tab.DASHBOARD -> stringResource(R.string.tab_my_menus)
    Tab.PROFILE -> stringResource(R.string.tab_profile)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToMerchant: (String) -> Unit = {},
    onNavigateToStats: () -> Unit,
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToCurrencyPreference: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()
    val tourViewModel: TourViewModel = hiltViewModel()
    val tourState by tourViewModel.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eatout_prefs", android.content.Context.MODE_PRIVATE) }

    val currentUser by authViewModel.currentUser.collectAsState()
    val pendingPhotoBitmap by authViewModel.pendingPhotoBitmap.collectAsState()
    val needsSetup by mainViewModel.needsSetup.collectAsState()
    val needsLocationPrompt by mainViewModel.needsLocationPrompt.collectAsState()
    val isMerchant = currentUser?.role == User.UserRole.COMERCIO
    // Flag local: una vez que el usuario termina el onboarding en esta sesión
    // hacemos swap a las tabs sin esperar a que SharedPreferences se relea.
    var onboardingFinishedThisSession by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(Tab.FEED) }

    // Decisión síncrona: sólo mostramos onboarding si (1) ya conocemos el uid,
    // (2) no se acaba de terminar en esta sesión y (3) SharedPreferences no
    // tiene persistida la marca. Evaluar esto aquí (en lugar de via
    // LaunchedEffect) evita que el feed parpadee antes de que el efecto se
    // dispare.
    val uidForOnboarding = currentUser?.id
    val showOnboarding = uidForOnboarding != null
        && !onboardingFinishedThisSession
        && !prefs.getBoolean("hasSeenOnboarding_$uidForOnboarding", false)

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

    LaunchedEffect(currentUser, showOnboarding) {
        if (currentUser != null && !showOnboarding) {
            val uid = currentUser!!.id
            if (!prefs.getBoolean("hasSeenTour_$uid", false)) {
                kotlinx.coroutines.delay(1500)
                tourViewModel.start(isMerchant = isMerchant)
            }
        }
    }

    if (showOnboarding) {
        AppOnboardingScreen(
            isMerchant = isMerchant,
            onFinish = {
                uidForOnboarding?.let {
                    prefs.edit().putBoolean("hasSeenOnboarding_$it", true).apply()
                }
                onboardingFinishedThisSession = true
            }
        )
        return
    }

    val tabs = if (isMerchant) listOf(Tab.FEED, Tab.FAVORITES, Tab.DASHBOARD, Tab.PROFILE)
    else listOf(Tab.FEED, Tab.FAVORITES, Tab.PROFILE)

    val hazeState = remember { HazeState() }
    // No bottom inset on the content — we want it to render BEHIND the navbar
    // so the Liquid-Glass blur captures real pixels. Last items may be partially
    // covered; scrollable screens should add their own contentPadding(bottom).
    val bottomInset = PaddingValues(0.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Content layer: marked as haze source so navbar can blur it.
        Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
            when (selectedTab) {
                Tab.FEED -> {
                    FeedScreen(
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToMerchant = onNavigateToMerchant,
                        onNavigateToProfile = { selectedTab = Tab.PROFILE },
                        onNavigateToLocation = onNavigateToLocation,
                        modifier = Modifier.padding(bottomInset),
                        tourViewModel = tourViewModel
                    )
                }
                Tab.FAVORITES -> {
                    com.sozolab.zampa.ui.favorites.FavoritesScreen(
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
                Tab.DASHBOARD -> {
                    DashboardScreen(
                        modifier = Modifier.padding(bottomInset),
                        tourViewModel = tourViewModel
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
                        onNavigateToCurrencyPreference = onNavigateToCurrencyPreference,
                        onNavigateToLanguage = onNavigateToLanguage,
                        onNavigateToLegal = onNavigateToLegal,
                        onRequestAccountDeletion = { onError -> authViewModel.requestAccountDeletion(onError) },
                        modifier = Modifier.padding(bottomInset)
                    )
                }
            }
        }

        // Floating Liquid-Glass navbar overlay.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(32.dp), clip = false)
                .clip(RoundedCornerShape(32.dp))
                .hazeChild(state = hazeState, style = HazeMaterials.thin())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = selectedTab == tab
                val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .then(
                            when (tab) {
                                Tab.FAVORITES -> Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    tourViewModel.registerBounds(
                                        TourTarget.FAVORITES_TAB,
                                        TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    )
                                }
                                Tab.DASHBOARD -> Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    tourViewModel.registerBounds(
                                        TourTarget.MERCHANT_DASHBOARD_TAB,
                                        TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    )
                                }
                                else -> Modifier
                            }
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when (tab) {
                            Tab.FEED -> Icons.Default.Restaurant
                            Tab.FAVORITES -> Icons.Default.Favorite
                            Tab.DASHBOARD -> Icons.Default.AddBox
                            Tab.PROFILE -> Icons.Default.Person
                        },
                        contentDescription = tab.label(),
                        tint = tint
                    )
                    Text(
                        tab.label(),
                        color = tint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (tourState.isActive) {
            TourOverlay(
                state = tourState,
                onNext = {
                    if (tourState.isLastStep) {
                        currentUser?.id?.let { uid ->
                            prefs.edit().putBoolean("hasSeenTour_$uid", true).apply()
                        }
                    }
                    tourViewModel.next()
                },
                onSkip = {
                    currentUser?.id?.let { uid ->
                        prefs.edit().putBoolean("hasSeenTour_$uid", true).apply()
                    }
                    tourViewModel.skip()
                }
            )
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
        Text(user?.name ?: stringResource(R.string.profile_user_default), style = MaterialTheme.typography.headlineMedium)
        Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            if (user?.role == User.UserRole.COMERCIO) stringResource(R.string.auth_restaurant) else stringResource(R.string.auth_client),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.profile_logout))
        }
    }
}
