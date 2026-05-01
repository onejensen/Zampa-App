package com.sozolab.zampa.ui.feed

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sozolab.zampa.R
import com.sozolab.zampa.ui.theme.ChipBackground
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.util.Calendar

@Composable
fun MenuDetailScreen(
    menuId: String,
    onBack: () -> Unit,
    onNavigateToMerchant: (String) -> Unit = {},
    viewModel: MenuDetailViewModel = hiltViewModel()
) {
    val authViewModel: com.sozolab.zampa.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val menu by viewModel.menu.collectAsState()
    val merchant by viewModel.merchant.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()

    var showFullImage by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showDirectionsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(menuId) { viewModel.load(menuId) }

    val distanceText: String? = remember(merchant, userLocation) {
        val addr = merchant?.address ?: return@remember null
        val loc = userLocation ?: return@remember null
        val merchantLoc = Location("").apply { latitude = addr.lat; longitude = addr.lng }
        val meters = loc.distanceTo(merchantLoc)
        if (meters < 1000) "${meters.toInt()}m" else "${"%.1f".format(meters / 1000)} km"
    }

    val strClosesAt = stringResource(R.string.detail_closes_at)
    val strOpensAt = stringResource(R.string.detail_opens_at)

    val openStatus: Pair<Boolean, String?> = remember(merchant, strClosesAt, strOpensAt) {
        val schedule = merchant?.schedule ?: return@remember false to null
        val cal = Calendar.getInstance()
        val keys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val todayKey = keys[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val entry = schedule.firstOrNull { it.day == todayKey } ?: return@remember false to null
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val nowMins = hour * 60 + minute

        fun timeMins(t: String): Int {
            val parts = t.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        val isOpen = nowMins >= timeMins(entry.open) && nowMins <= timeMins(entry.close)
        val label = if (isOpen) "$strClosesAt ${formatScheduleTime(entry.close)}"
                    else "$strOpensAt ${formatScheduleTime(entry.open)}"
        isOpen to label
    }

    if (isLoading && menu == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentMenu = menu ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.detail_could_not_load))
                TextButton(onClick = onBack) { Text(stringResource(R.string.detail_back)) }
            }
        }
        return
    }

    val priceRange = when {
        currentMenu.priceTotal < 10 -> "$"
        currentMenu.priceTotal < 20 -> "$$"
        else -> "$$$"
    }

    // Directions dialog
    if (showDirectionsDialog) {
        AlertDialog(
            onDismissRequest = { showDirectionsDialog = false },
            title = { Text(stringResource(R.string.detail_directions)) },
            text = {
                val addr = merchant?.address
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Google Maps — try app first, fall back to web
                    OutlinedButton(
                        onClick = {
                            showDirectionsDialog = false
                            viewModel.trackAction("directions")
                            viewModel.saveHistoryEntry("directions")
                            val hasCoords = addr != null && (addr.lat != 0.0 || addr.lng != 0.0)
                            val destination = if (hasCoords && addr != null)
                                "${addr.lat},${addr.lng}"
                            else
                                Uri.encode(addr?.formatted ?: merchant?.name ?: "")
                            // Try Google Maps app (setPackage works without <queries> for explicit launch)
                            try {
                                val gmmUri = if (hasCoords && addr != null)
                                    Uri.parse("google.navigation:q=${addr.lat},${addr.lng}&mode=d")
                                else
                                    Uri.parse("google.navigation:q=$destination&mode=d")
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, gmmUri).setPackage("com.google.android.apps.maps")
                                )
                            } catch (_: Exception) {
                                // Google Maps not installed — open in browser
                                val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=driving")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(stringResource(R.string.detail_google_maps)) }

                    // Generic geo intent (opens default maps app)
                    OutlinedButton(
                        onClick = {
                            showDirectionsDialog = false
                            viewModel.trackAction("directions")
                            viewModel.saveHistoryEntry("directions")
                            val hasCoords = addr != null && (addr.lat != 0.0 || addr.lng != 0.0)
                            val geoUri = if (hasCoords && addr != null)
                                Uri.parse("geo:${addr.lat},${addr.lng}?q=${addr.lat},${addr.lng}(${Uri.encode(addr.formatted)})")
                            else
                                Uri.parse("geo:0,0?q=${Uri.encode(addr?.formatted ?: merchant?.name ?: "")}")
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            } catch (_: Exception) {
                                val destination = if (hasCoords && addr != null) "${addr.lat},${addr.lng}" else Uri.encode(addr?.formatted ?: "")
                                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$destination")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(stringResource(R.string.detail_other_apps)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDirectionsDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── TOP BAR ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.detail_back))
            }
            Text(
                merchant?.name ?: currentMenu.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = {
                // Compartimos un App Link verificado al landing de Firebase Hosting.
                // El receptor abre la app si la tiene instalada (verified App Link),
                // o aterriza en la landing que le redirige a la Play Store / App Store.
                val shareUrl = "https://www.getzampa.com/o/${currentMenu.id}"
                val sharePrefix = context.getString(R.string.detail_share_text)
                val shareText = "$sharePrefix ${currentMenu.title}!\n$shareUrl"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, currentMenu.title)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
                viewModel.trackAction("share")
            }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.detail_share))
            }
        }

        // ── SCROLLABLE CONTENT ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── HERO IMAGE ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clickable { if (currentMenu.photoUrls.isNotEmpty()) showFullImage = true }
            ) {
                AsyncImage(
                    model = currentMenu.photoUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 200f
                            )
                        )
                )

                // Bottom-left: name, address, open/closed
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp, end = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cuisine + price range tags
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        merchant?.cuisineTypes?.firstOrNull()?.let { cuisine ->
                            TagPill(cuisine)
                        }
                        TagPill(priceRange)
                    }

                    // Restaurant name — tappable → abre perfil público del comercio
                    Row(
                        modifier = Modifier.clickable { onNavigateToMerchant(currentMenu.businessId) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            merchant?.name ?: currentMenu.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Location row
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(13.dp).padding(top = 1.dp), tint = Color.White.copy(0.9f))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            merchant?.address?.let { addr ->
                                Text(
                                    addr.formatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(0.9f),
                                    maxLines = 2
                                )
                            }
                            if (distanceText != null) {
                                Text(
                                    distanceText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(0.8f)
                                )
                            }
                        }
                    }

                    // Open / closed pill
                    if (merchant?.schedule != null) {
                        val isOpen = openStatus.first
                        val statusLabel = if (isOpen) stringResource(R.string.detail_open_now) else stringResource(R.string.detail_closed_now)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (isOpen) Color(0xFF22C55E) else Color(0xFFEF4444),
                                        CircleShape
                                    )
                            )
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            openStatus.second?.let { label ->
                                Text("· $label", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                            }
                        }
                    }
                }

                // Bottom-right: heart button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .clickable { viewModel.toggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.detail_favorite),
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider()

            // ── ACTION BUTTONS ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlineDetailButton(icon = Icons.Default.Phone, label = stringResource(R.string.detail_call), modifier = Modifier.weight(1f)) {
                    merchant?.phone?.let { p ->
                        viewModel.trackAction("call")
                        viewModel.saveHistoryEntry("call")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p"))
                        context.startActivity(intent)
                    }
                }
                OutlineDetailButton(icon = Icons.Default.Directions, label = stringResource(R.string.detail_directions), modifier = Modifier.weight(1f)) {
                    showDirectionsDialog = true
                }
            }

            // ── ADDRESS ROW ──────────────────────────────────────────────
            merchant?.address?.let { addr ->
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            addr.formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        distanceText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── CATEGORY TABS (informativos, no seleccionables) ──────────
            currentMenu.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            // Gris neutro semi-translúcido — funciona sobre cualquier fondo
                            // y mantiene texto blanco legible en light + dark.
                            color = Color.Black.copy(alpha = 0.4f),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()
            }

            // ── MENU ITEM ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Section header
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp).padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text(
                        selectedTag ?: currentMenu.tags?.firstOrNull() ?: stringResource(R.string.feed_menu_del_dia),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    currentMenu.offerType?.let { type ->
                        val pillColor = Color(0xFFE85D3F)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = pillColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                offerTypeLabel(type),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = pillColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Includes chips
                val strDrink = stringResource(R.string.create_menu_drink)
                val strDessert = stringResource(R.string.create_menu_dessert)
                val strCoffee = stringResource(R.string.create_menu_coffee)
                val includes = buildList {
                    if (currentMenu.includesDrink)   add(strDrink)
                    if (currentMenu.includesDessert) add(strDessert)
                    if (currentMenu.includesCoffee)  add(strCoffee)
                }
                if (includes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        includes.forEach { label ->
                            Surface(shape = RoundedCornerShape(8.dp), color = ChipBackground) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Item row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = currentMenu.photoUrls.firstOrNull(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(currentMenu.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                        currentMenu.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                com.sozolab.zampa.data.CurrencyService.format(currentMenu.priceTotal, "EUR"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val prefCode = currentUser?.currencyPreference ?: "EUR"
                            if (prefCode != "EUR") {
                                val converted = remember(currentMenu.priceTotal, prefCode) {
                                    authViewModel.formatConverted(currentMenu.priceTotal, prefCode)
                                }
                                converted?.let {
                                    Text(
                                        "~$it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // ── DIETARY INFO ─────────────────────────────────────────────
            if (currentMenu.dietaryInfo.hasAnyInfo) {
                HorizontalDivider()
                DietaryInfoSection(dietaryInfo = currentMenu.dietaryInfo)
            }

            // ── SHORT DESCRIPTION ────────────────────────────────────────
            merchant?.shortDescription?.takeIf { it.isNotEmpty() }?.let { desc ->
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.detail_about_restaurant), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Fullscreen image
    if (showFullImage && currentMenu.photoUrls.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = currentMenu.photoUrls.first(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// MARK: - Reusable composables

@Composable
private fun TagPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = Color.White,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun OutlineDetailButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfoItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlineDetailButton(icon = icon, label = label, modifier = modifier, onClick = onClick)
}

@Composable
fun DetailActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlineDetailButton(icon = icon, label = label, modifier = modifier, onClick = onClick)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryInfoSection(dietaryInfo: com.sozolab.zampa.data.model.DietaryInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.detail_dietary_info), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        }

        // Diet + protein badges
        val dietItems = buildList {
            if (dietaryInfo.isVegan) add("\uD83C\uDF3F ${stringResource(R.string.dietary_vegan)}")
            else if (dietaryInfo.isVegetarian) add("\uD83E\uDD57 ${stringResource(R.string.dietary_vegetarian)}")
            if (dietaryInfo.hasMeat) add("\uD83C\uDF56 ${stringResource(R.string.create_menu_meat)}")
            if (dietaryInfo.hasFish) add("\uD83D\uDC1F ${stringResource(R.string.create_menu_fish)}")
        }
        if (dietItems.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dietItems.forEach { label ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Allergens
        val allergens = buildList {
            if (dietaryInfo.hasGluten)  add(stringResource(R.string.create_menu_gluten))
            if (dietaryInfo.hasLactose) add(stringResource(R.string.create_menu_dairy))
            if (dietaryInfo.hasNuts)    add(stringResource(R.string.create_menu_nuts))
            if (dietaryInfo.hasEgg)     add(stringResource(R.string.create_menu_egg))
        }
        if (allergens.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFF97316), modifier = Modifier.size(14.dp))
                Text(
                    "${stringResource(R.string.detail_allergens)} ${allergens.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF97316)
                )
            }
        }
    }
}

/**
 * Normaliza el string de horario a "HH:mm" (24h) independientemente del
 * formato de entrada: "10:00", "10:00:00", "10:00Z", "2026-04-11T10:00:00Z".
 */
private fun formatScheduleTime(time: String): String {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.takeLast(2)?.toIntOrNull() ?: return time
    val m = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0
    return "%02d:%02d".format(h, m)
}
