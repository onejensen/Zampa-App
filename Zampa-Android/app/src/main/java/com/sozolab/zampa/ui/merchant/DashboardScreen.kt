package com.sozolab.zampa.ui.merchant

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import com.sozolab.zampa.ui.tour.TourBounds
import com.sozolab.zampa.ui.tour.TourTarget
import com.sozolab.zampa.ui.tour.TourViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sozolab.zampa.R
import com.sozolab.zampa.data.model.DietaryInfo
import com.sozolab.zampa.data.model.Menu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    tourViewModel: TourViewModel? = null
) {
    val menus by viewModel.menus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    val merchant by viewModel.merchant.collectAsState()
    val promoFreeUntilMs by viewModel.promoFreeUntilMs.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingMenu by remember { mutableStateOf<Menu?>(null) }
    var showSubscriptionSheet by remember { mutableStateOf(false) }

    var isSelecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.merchant_dashboard), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── BANNER DE SUSCRIPCIÓN ──────────────────────────────────
            item {
                SubscriptionBanner(
                    merchant = merchant,
                    promoFreeUntilMs = promoFreeUntilMs,
                    onClick = { showSubscriptionSheet = true }
                )
            }

            // ── STATS GRID ──────────────────────────────────────────────
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.MERCHANT_STATS_GRID,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            icon = Icons.Default.Visibility,
                            title = stringResource(R.string.merchant_views_today),
                            value = "${todayStats.impressions}",
                            iconColor = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.TouchApp,
                            title = stringResource(R.string.merchant_clicks_today),
                            value = "${todayStats.clicks}",
                            iconColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            icon = Icons.Default.Favorite,
                            title = stringResource(R.string.merchant_favorites),
                            value = "${todayStats.favorites}",
                            iconColor = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.RestaurantMenu,
                            title = stringResource(R.string.merchant_active_menus),
                            value = "${menus.count { it.isToday }}",
                            iconColor = Color(0xFF22C55E),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── BIG PUBLISH BUTTON ──────────────────────────────────────
            item {
                val primary = MaterialTheme.colorScheme.primary
                Button(
                    onClick = { showCreateSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = primary,
                            spotColor = primary
                        )
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tourViewModel?.registerBounds(
                                TourTarget.MERCHANT_CREATE_BUTTON,
                                TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.merchant_publish),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // ── "MIS OFERTAS" HEADER ────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.merchant_my_offers),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    if (menus.isNotEmpty()) {
                        TextButton(onClick = {
                            isSelecting = !isSelecting
                            if (!isSelecting) selectedIds.clear()
                        }) {
                            Text(if (isSelecting) stringResource(R.string.merchant_done) else stringResource(R.string.merchant_edit))
                        }
                    }
                }
            }

            // ── MENUS LIST ──────────────────────────────────────────────
            if (isLoading && menus.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (menus.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddBox,
                                contentDescription = null,
                                modifier = Modifier.size(60.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.merchant_no_menus),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showCreateSheet = true }) {
                                Text(stringResource(R.string.merchant_first_menu))
                            }
                        }
                    }
                }
            } else {
                items(menus, key = { it.id }) { menu ->
                    MerchantMenuRow(
                        menu = menu,
                        isSelecting = isSelecting,
                        isSelected = menu.id in selectedIds,
                        onClick = {
                            if (isSelecting) {
                                if (menu.id in selectedIds) selectedIds.remove(menu.id)
                                else selectedIds.add(menu.id)
                            } else if (menu.isToday) {
                                editingMenu = menu
                            }
                        },
                        onDelete = { viewModel.deleteMenu(menu.id) }
                    )
                }

                if (isSelecting && selectedIds.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { showBulkDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.merchant_delete_selected) + " (${selectedIds.size})")
                        }
                    }
                }
            }
        }

        // Bottom bar for bulk delete
        if (isSelecting && selectedIds.isNotEmpty()) {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { showBulkDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.merchant_delete_selected) + " (${selectedIds.size})")
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateMenuSheet(viewModel = viewModel, onDismiss = { showCreateSheet = false })
    }

    editingMenu?.let { menu ->
        EditMenuSheet(menu = menu, viewModel = viewModel, onDismiss = { editingMenu = null })
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(R.string.merchant_delete_count, selectedIds.size)) },
            text = { Text(stringResource(R.string.merchant_delete_selected_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMenus(selectedIds.toList())
                    selectedIds.clear()
                    isSelecting = false
                    showBulkDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showSubscriptionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubscriptionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            com.sozolab.zampa.ui.subscription.SubscriptionScreen(
                onDismiss = { showSubscriptionSheet = false }
            )
        }
    }
}

/**
 * Banner clickable que muestra el estado de suscripción del merchant:
 * promo global → gratis hasta X, trial → días restantes, expirada → CTA.
 */
@Composable
private fun SubscriptionBanner(
    merchant: com.sozolab.zampa.data.model.Merchant?,
    promoFreeUntilMs: Long?,
    onClick: () -> Unit,
) {
    val nowMs = System.currentTimeMillis()
    val promoActive = (promoFreeUntilMs ?: 0L) > nowMs
    val trialDays = merchant?.trialDaysRemaining()

    val (icon, title, subtitle) = when {
        promoActive -> {
            val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, java.util.Locale.getDefault())
            Triple(
                Icons.Default.CardGiftcard,
                stringResource(R.string.subscription_promo_title),
                stringResource(R.string.subscription_promo_body, fmt.format(java.util.Date(promoFreeUntilMs!!)))
            )
        }
        trialDays == null -> Triple(Icons.Default.Star, stringResource(R.string.subscription_title), null)
        trialDays <= 0 -> Triple(
            Icons.Default.Warning,
            stringResource(R.string.subscription_trial_ends_today),
            stringResource(R.string.subscription_expired_body)
        )
        else -> Triple(
            Icons.Default.HourglassBottom,
            stringResource(R.string.subscription_trial_days_remaining, trialDays),
            null
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - Offer Details Section (shared by Create & Edit sheets)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfferDetailsSection(
    offerType: String?,
    onOfferTypeChange: (String?) -> Unit,
    includesDrink: Boolean, onDrinkChange: (Boolean) -> Unit,
    includesDessert: Boolean, onDessertChange: (Boolean) -> Unit,
    includesCoffee: Boolean, onCoffeeChange: (Boolean) -> Unit,
    serviceTime: String = "both", onServiceTimeChange: (String) -> Unit = {},
) {
    // (value, label): `value` es el valor canónico ES que se guarda en Firestore;
    // `label` es la traducción que se muestra al usuario. Nunca persistir `label`.
    val offerTypeOptions = listOf(
        com.sozolab.zampa.ui.feed.OfferTypes.MENU_DEL_DIA to stringResource(R.string.create_menu_menu_del_dia),
        com.sozolab.zampa.ui.feed.OfferTypes.PLATO_DEL_DIA to stringResource(R.string.create_menu_plato_del_dia),
        com.sozolab.zampa.ui.feed.OfferTypes.OFERTA_DEL_DIA to stringResource(R.string.create_menu_oferta_del_dia),
        com.sozolab.zampa.ui.feed.OfferTypes.OFERTA_PERMANENTE to stringResource(R.string.create_menu_permanent),
    )
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.create_menu_offer_type), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offerTypeOptions.forEach { (value, label) ->
                    val isSelected = offerType == value
                    FilterChip(
                        selected = isSelected,
                        onClick = { onOfferTypeChange(if (isSelected) null else value) },
                        label = { Text(label) },
                        colors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                    )
                }
            }

            // Horario de la oferta
            Text(stringResource(R.string.create_menu_schedule), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("lunch" to stringResource(R.string.create_menu_midday), "dinner" to stringResource(R.string.create_menu_night), "both" to stringResource(R.string.create_menu_both)).forEach { (value, label) ->
                    FilterChip(
                        selected = serviceTime == value,
                        onClick = { onServiceTimeChange(value) },
                        label = { Text(label) },
                        colors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                    )
                }
            }

            Text(stringResource(R.string.create_menu_includes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val brandColors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                FilterChip(selected = includesDrink,   onClick = { onDrinkChange(!includesDrink) },     label = { Text(stringResource(R.string.create_menu_drink)) }, colors = brandColors)
                FilterChip(selected = includesDessert, onClick = { onDessertChange(!includesDessert) }, label = { Text(stringResource(R.string.create_menu_dessert)) }, colors = brandColors)
                FilterChip(selected = includesCoffee,  onClick = { onCoffeeChange(!includesCoffee) },   label = { Text(stringResource(R.string.create_menu_coffee)) }, colors = brandColors)
            }
        }
    }
}

// MARK: - Dietary Info Editor (shared by Create & Edit sheets)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietaryInfoEditor(
    isVegetarian: Boolean, onVegetarianChange: (Boolean) -> Unit,
    isVegan: Boolean, onVeganChange: (Boolean) -> Unit,
    hasMeat: Boolean, onMeatChange: (Boolean) -> Unit,
    hasFish: Boolean, onFishChange: (Boolean) -> Unit,
    hasGluten: Boolean, onGlutenChange: (Boolean) -> Unit,
    hasLactose: Boolean, onLactoseChange: (Boolean) -> Unit,
    hasNuts: Boolean, onNutsChange: (Boolean) -> Unit,
    hasEgg: Boolean, onEggChange: (Boolean) -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.create_menu_dietary_info), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))

            // Diet
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.create_menu_diet), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val bc = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                    FilterChip(selected = isVegetarian, onClick = { onVegetarianChange(!isVegetarian) }, label = { Text(stringResource(R.string.create_menu_vegetarian)) }, colors = bc)
                    FilterChip(selected = isVegan, onClick = { onVeganChange(!isVegan) }, label = { Text(stringResource(R.string.create_menu_vegan)) }, colors = bc)
                }
            }

            // Protein
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.create_menu_protein), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val bc = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                    FilterChip(selected = hasMeat, onClick = { onMeatChange(!hasMeat) }, label = { Text(stringResource(R.string.create_menu_meat)) }, colors = bc)
                    FilterChip(selected = hasFish, onClick = { onFishChange(!hasFish) }, label = { Text(stringResource(R.string.create_menu_fish)) }, colors = bc)
                }
            }

            // Allergens
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.create_menu_allergens_present), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val bc = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                    FilterChip(selected = hasGluten,  onClick = { onGlutenChange(!hasGluten) },   label = { Text(stringResource(R.string.create_menu_gluten)) }, colors = bc)
                    FilterChip(selected = hasLactose, onClick = { onLactoseChange(!hasLactose) }, label = { Text(stringResource(R.string.create_menu_dairy)) }, colors = bc)
                    FilterChip(selected = hasNuts,    onClick = { onNutsChange(!hasNuts) },       label = { Text(stringResource(R.string.create_menu_nuts)) }, colors = bc)
                    FilterChip(selected = hasEgg,     onClick = { onEggChange(!hasEgg) },         label = { Text(stringResource(R.string.create_menu_egg)) }, colors = bc)
                }
            }
        }
    }
}

// MARK: - Stat Card

@Composable
private fun StatCard(
    icon: ImageVector,
    title: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMenuRow(
    menu: Menu,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteConfirm = true
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (menu.isToday) onClick()
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        gesturesEnabled = !isSelecting,
        enableDismissFromStartToEnd = menu.isToday && !isSelecting,
        enableDismissFromEndToStart = !isSelecting,
        backgroundContent = {
            val isEndToStart = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val isStartToEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            val bgColor = when {
                isEndToStart -> MaterialTheme.colorScheme.errorContainer
                isStartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp)
            ) {
                when {
                    isEndToStart -> Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    isStartToEnd -> Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isSelecting) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                    Spacer(Modifier.width(4.dp))
                }
                if (menu.photoUrls.isNotEmpty()) {
                    AsyncImage(
                        model = menu.photoUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(menu.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        String.format("%.2f €", menu.priceTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatMenuDate(menu.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!menu.isToday) {
                        Text(stringResource(R.string.merchant_expired), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!isSelecting) {
                    if (menu.isToday) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.merchant_delete_menu_question)) },
            text = { Text(stringResource(R.string.merchant_delete_menu_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMenuSheet(menu: Menu, viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(menu.title) }
    var description by remember { mutableStateOf(menu.description ?: "") }
    var priceText by remember { mutableStateOf(menu.priceTotal.toString()) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<ByteArray?>(null) }
    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(menu.tags ?: emptyList()) } }
    var availableTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isPremium by viewModel.isPremium.collectAsState()
    var dietIsVegetarian by remember { mutableStateOf(menu.dietaryInfo.isVegetarian) }
    var dietIsVegan by remember { mutableStateOf(menu.dietaryInfo.isVegan) }
    var dietHasMeat by remember { mutableStateOf(menu.dietaryInfo.hasMeat) }
    var dietHasFish by remember { mutableStateOf(menu.dietaryInfo.hasFish) }
    var dietHasGluten by remember { mutableStateOf(menu.dietaryInfo.hasGluten) }
    var dietHasLactose by remember { mutableStateOf(menu.dietaryInfo.hasLactose) }
    var dietHasNuts by remember { mutableStateOf(menu.dietaryInfo.hasNuts) }
    var dietHasEgg by remember { mutableStateOf(menu.dietaryInfo.hasEgg) }
    var offerType by remember { mutableStateOf(menu.offerType) }
    var inclDrink by remember { mutableStateOf(menu.includesDrink) }
    var inclDessert by remember { mutableStateOf(menu.includesDessert) }
    var inclCoffee by remember { mutableStateOf(menu.includesCoffee) }
    var serviceTime by remember { mutableStateOf(menu.serviceTime) }

    val isLoading by viewModel.isLoading.collectAsState()
    val success by viewModel.createSuccess.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            imageData = context.contentResolver.openInputStream(it)?.readBytes()
        }
    }

    LaunchedEffect(success) {
        if (success) {
            viewModel.resetCreateSuccess()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        availableTags = try {
            com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
                Text(
                    stringResource(R.string.create_menu_edit_menu),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.create_menu_title_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.create_menu_description_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText, onValueChange = { priceText = it }, label = { Text(stringResource(R.string.create_menu_price_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.create_menu_cuisine_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        label = { Text(tag, maxLines = 1, softWrap = false) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OfferDetailsSection(
                offerType = offerType, onOfferTypeChange = { offerType = it },
                includesDrink = inclDrink, onDrinkChange = { inclDrink = it },
                includesDessert = inclDessert, onDessertChange = { inclDessert = it },
                includesCoffee = inclCoffee, onCoffeeChange = { inclCoffee = it },
                serviceTime = serviceTime, onServiceTimeChange = { serviceTime = it },
            )
            Spacer(Modifier.height(12.dp))

            DietaryInfoEditor(
                isVegetarian = dietIsVegetarian, onVegetarianChange = { dietIsVegetarian = it },
                isVegan = dietIsVegan, onVeganChange = { dietIsVegan = it },
                hasMeat = dietHasMeat, onMeatChange = { dietHasMeat = it },
                hasFish = dietHasFish, onFishChange = { dietHasFish = it },
                hasGluten = dietHasGluten, onGlutenChange = { dietHasGluten = it },
                hasLactose = dietHasLactose, onLactoseChange = { dietHasLactose = it },
                hasNuts = dietHasNuts, onNutsChange = { dietHasNuts = it },
                hasEgg = dietHasEgg, onEggChange = { dietHasEgg = it },
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = imageUri ?: menu.photoUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Photo, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_menu_change_photo))
            }
            Spacer(Modifier.height(20.dp))

            val price = priceText.toDoubleOrNull() ?: 0.0
            val dietary = DietaryInfo(dietIsVegetarian, dietIsVegan, dietHasMeat, dietHasFish, dietHasGluten, dietHasLactose, dietHasNuts, dietHasEgg)
            Button(
                onClick = {
                    viewModel.updateMenu(menu.id, title, description, price, selectedTags.toList(), imageData, dietary, offerType, inclDrink, inclDessert, inclCoffee)
                },
                enabled = !isLoading && title.isNotBlank() && price > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.create_menu_save_changes))
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.merchant_delete_menu))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.merchant_delete_menu_question)) },
            text = { Text(stringResource(R.string.merchant_delete_menu_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMenu(menu.id)
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMenuSheet(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<ByteArray?>(null) }
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    val selectedTags = remember { mutableStateListOf<String>() }
    var availableTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var mealType by remember { mutableStateOf("Comida") }
    val isPremium by viewModel.isPremium.collectAsState()

    var dietIsVegetarian by remember { mutableStateOf(false) }
    var dietIsVegan by remember { mutableStateOf(false) }
    var dietHasMeat by remember { mutableStateOf(false) }
    var dietHasFish by remember { mutableStateOf(false) }
    var dietHasGluten by remember { mutableStateOf(false) }
    var dietHasLactose by remember { mutableStateOf(false) }
    var dietHasNuts by remember { mutableStateOf(false) }
    var dietHasEgg by remember { mutableStateOf(false) }
    var offerType by remember { mutableStateOf<String?>(null) }
    var inclDrink by remember { mutableStateOf(false) }
    var inclDessert by remember { mutableStateOf(false) }
    var inclCoffee by remember { mutableStateOf(false) }
    var serviceTime by remember { mutableStateOf("both") }

    val isLoading by viewModel.isLoading.collectAsState()
    val createSuccess by viewModel.createSuccess.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            cameraBitmap = null
            imageData = context.contentResolver.openInputStream(it)?.readBytes()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            cameraBitmap = bitmap
            imageUri = null
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            imageData = stream.toByteArray()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) cameraLauncher.launch(null)
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(stringResource(R.string.create_menu_photo)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_camera)) }
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_gallery)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    LaunchedEffect(createSuccess) {
        if (createSuccess) {
            viewModel.resetCreateSuccess()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        availableTags = try {
            com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
                Text(
                    stringResource(R.string.create_menu_new_menu),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            // Photo picker with preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showPhotoSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                when {
                    cameraBitmap != null -> Image(
                        bitmap = cameraBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    imageUri != null -> AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.create_menu_add_photo), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Meal type selector
            val mealTypeLabels = mapOf("Comida" to stringResource(R.string.create_menu_lunch), "Cena" to stringResource(R.string.create_menu_dinner))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Comida", "Cena").forEach { type ->
                    val isSelected = mealType == type
                    Surface(
                        onClick = { mealType = type },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            mealTypeLabels[type] ?: type,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.create_menu_title_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.create_menu_description_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText, onValueChange = { priceText = it }, label = { Text(stringResource(R.string.create_menu_price_label)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.create_menu_cuisine_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        label = { Text(tag, maxLines = 1, softWrap = false) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OfferDetailsSection(
                offerType = offerType, onOfferTypeChange = { offerType = it },
                includesDrink = inclDrink, onDrinkChange = { inclDrink = it },
                includesDessert = inclDessert, onDessertChange = { inclDessert = it },
                includesCoffee = inclCoffee, onCoffeeChange = { inclCoffee = it },
                serviceTime = serviceTime, onServiceTimeChange = { serviceTime = it },
            )
            Spacer(Modifier.height(12.dp))

            DietaryInfoEditor(
                isVegetarian = dietIsVegetarian, onVegetarianChange = { dietIsVegetarian = it },
                isVegan = dietIsVegan, onVeganChange = { dietIsVegan = it },
                hasMeat = dietHasMeat, onMeatChange = { dietHasMeat = it },
                hasFish = dietHasFish, onFishChange = { dietHasFish = it },
                hasGluten = dietHasGluten, onGlutenChange = { dietHasGluten = it },
                hasLactose = dietHasLactose, onLactoseChange = { dietHasLactose = it },
                hasNuts = dietHasNuts, onNutsChange = { dietHasNuts = it },
                hasEgg = dietHasEgg, onEggChange = { dietHasEgg = it },
            )
            Spacer(Modifier.height(20.dp))

            val price = priceText.toDoubleOrNull() ?: 0.0
            val tags = if (selectedTags.isEmpty()) listOf(mealType) else selectedTags.toList()
            val dietary = DietaryInfo(dietIsVegetarian, dietIsVegan, dietHasMeat, dietHasFish, dietHasGluten, dietHasLactose, dietHasNuts, dietHasEgg)
            Button(
                onClick = { imageData?.let { viewModel.createMenu(title, description, price, it, tags, dietary, offerType, inclDrink, inclDessert, inclCoffee, serviceTime, offerType == com.sozolab.zampa.ui.feed.OfferTypes.OFERTA_PERMANENTE) } },
                enabled = !isLoading && title.isNotBlank() && price > 0 && imageData != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.create_menu_publish))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatMenuDate(iso: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.take(19)) ?: return iso
        val display = java.text.SimpleDateFormat("dd/MM/yy  HH:mm", java.util.Locale.getDefault())
        display.timeZone = java.util.TimeZone.getDefault()
        display.format(date)
    } catch (_: Exception) { iso }
}
