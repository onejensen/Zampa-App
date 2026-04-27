package com.sozolab.zampa.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import com.sozolab.zampa.ui.tour.TourBounds
import com.sozolab.zampa.ui.tour.TourTarget
import com.sozolab.zampa.ui.tour.TourViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sozolab.zampa.R
import coil.compose.AsyncImage
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.Menu
import com.sozolab.zampa.data.model.Merchant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SortOption { DISTANCE, PRICE }
enum class FeedViewMode { LIST, MAP }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToMerchant: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onNavigateToLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
    tourViewModel: TourViewModel? = null
) {
    val authViewModel: com.sozolab.zampa.ui.auth.AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val menus by viewModel.menus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val merchantMap by viewModel.merchantMap.collectAsState()

    // Re-fetch location when returning from permission screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.fetchLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // `rememberSaveable` para preservar la vista (Lista/Mapa) y el orden elegido
    // cuando el user navega al detalle de una oferta y vuelve. Enum → Serializable
    // autom. Si usara `remember`, volver del detail resetea al default LIST.
    var sortOption by rememberSaveable { mutableStateOf(SortOption.DISTANCE) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeFilters by remember { mutableStateOf(ActiveFilters()) }
    var selectedCuisine by rememberSaveable { mutableStateOf<String?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(FeedViewMode.LIST) }

    val listState = rememberLazyListState()

    val sortedMenus = remember(menus, sortOption, selectedCuisine, activeFilters.onlyOpen, activeFilters.offerType, merchantMap, userLocation) {
        var result = menus
        activeFilters.offerType?.let { type ->
            result = result.filter { menu ->
                if (type == OfferTypes.OFERTA_PERMANENTE) {
                    menu.isPermanent || menu.offerType == OfferTypes.OFERTA_PERMANENTE
                } else {
                    menu.offerType == type
                }
            }
        }
        if (sortOption == SortOption.PRICE) {
            result = result.sortedBy { it.priceTotal }
        } else if (sortOption == SortOption.DISTANCE && userLocation != null) {
            val uLoc = userLocation!!
            result = result.sortedBy { menu ->
                val addr = merchantMap[menu.businessId]?.address
                if (addr != null) {
                    val mLoc = android.location.Location("").apply {
                        latitude = addr.lat
                        longitude = addr.lng
                    }
                    uLoc.distanceTo(mLoc).toDouble()
                } else {
                    Double.MAX_VALUE
                }
            }
        }
        result
    }

    val greetingHourBracket = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> 0 // morning
            hour < 20 -> 1 // afternoon
            else -> 2      // evening
        }
    }
    val greeting = when (greetingHourBracket) {
        0 -> stringResource(R.string.feed_good_morning)
        1 -> stringResource(R.string.feed_good_afternoon)
        else -> stringResource(R.string.feed_good_evening)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── HEADER ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (greetingHourBracket == 2) stringResource(R.string.feed_dinner_question) else stringResource(R.string.feed_lunch_question),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Image(
                painter = painterResource(R.drawable.logo_zampa),
                contentDescription = "Zampa",
                modifier = Modifier.size(32.dp)
            )
        }

        // ── SECTION HEADER ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (sortOption == SortOption.PRICE) stringResource(R.string.feed_by_price) else stringResource(R.string.feed_nearby),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            if (menus.isNotEmpty()) {
                Text(
                    "${sortedMenus.size} ${stringResource(R.string.feed_found)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── SORT + FILTER ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort pills solo tienen sentido en vista lista; el mapa ya muestra
                // posición/distancia visualmente y no admite "orden".
                if (viewMode == FeedViewMode.LIST) {
                    SortPill(
                        title = stringResource(R.string.feed_distance),
                        icon = Icons.Default.LocationOn,
                        isSelected = sortOption == SortOption.DISTANCE,
                        onClick = { sortOption = SortOption.DISTANCE }
                    )
                    SortPill(
                        title = stringResource(R.string.feed_price),
                        icon = Icons.Default.AttachMoney,
                        isSelected = sortOption == SortOption.PRICE,
                        onClick = { sortOption = SortOption.PRICE }
                    )
                }
            }
            IconButton(
                onClick = {
                    viewMode = if (viewMode == FeedViewMode.LIST) FeedViewMode.MAP else FeedViewMode.LIST
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (viewMode == FeedViewMode.MAP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp)
                    )
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.MAP_TOGGLE,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
            ) {
                Icon(
                    if (viewMode == FeedViewMode.LIST) Icons.Default.Map else Icons.Default.ViewList,
                    contentDescription = stringResource(
                        if (viewMode == FeedViewMode.LIST) R.string.feed_view_map else R.string.feed_view_list
                    ),
                    tint = if (viewMode == FeedViewMode.MAP) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .padding(end = 20.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.FILTER_BUTTON,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
            ) {
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.feed_filters), tint = MaterialTheme.colorScheme.onSurface)
                }
                if (activeFilters.isActive) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {}
                }
            }
        }

        // ── CONTENT ──────────────────────────────────────────────────────
        if (isLoading && menus.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && menus.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.feed_no_connection),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.loadMenus() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        } else if (sortedMenus.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.feed_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeFilters.isActive) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        activeFilters = ActiveFilters()
                        selectedCuisine = null
                        viewModel.applyFilters(null, null, null, false)
                    }) { Text(stringResource(R.string.feed_clear_filters)) }
                }
            }
        } else if (viewMode == FeedViewMode.MAP) {
            FeedMapView(
                menus = sortedMenus,
                merchantMap = merchantMap,
                userLocation = userLocation,
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToMerchant = onNavigateToMerchant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.loadMenus() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedMenus) { menu ->
                        MenuCard(
                            menu = menu,
                            onNavigateToDetail = onNavigateToDetail,
                            userLocation = userLocation,
                            modifier = if (menu == sortedMenus.firstOrNull())
                                Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    tourViewModel?.registerBounds(
                                        TourTarget.FEED_CARD,
                                        TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    )
                                }
                            else Modifier
                        )
                    }

                    if (viewModel.canLoadMore) {
                        item {
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            initialFilters = activeFilters,
            onDismiss = { showFilterSheet = false },
            onApply = { cuisine, price, distanceKm, favOnly, openOnly, offerType ->
                activeFilters = ActiveFilters(cuisine, price, distanceKm, favOnly, openOnly, offerType)
                selectedCuisine = cuisine
                viewModel.applyFilters(cuisine, price, distanceKm, favOnly)
                showFilterSheet = false
            },
            currencyPreference = currentUser?.currencyPreference,
            formatConverted = { amount, code -> authViewModel.formatConverted(amount, code) }
        )
    }
}

// MARK: - Sort Pill

@Composable
fun SortPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// MARK: - Menu Card

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MenuCard(
    menu: Menu,
    onNavigateToDetail: (String) -> Unit,
    userLocation: android.location.Location? = null,
    onMerchantLoaded: (String, Merchant) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var merchant by remember { mutableStateOf<Merchant?>(null) }

    LaunchedEffect(menu.businessId) {
        try {
            val m = FirebaseService().getMerchantProfile(menu.businessId)
            if (m != null) {
                merchant = m
                onMerchantLoaded(menu.businessId, m)
            }
        } catch (_: Exception) {}
    }

    val distanceText: String? = remember(merchant, userLocation) {
        val addr = merchant?.address ?: return@remember null
        val loc = userLocation ?: return@remember null
        val merchantLoc = android.location.Location("").apply {
            latitude = addr.lat
            longitude = addr.lng
        }
        val meters = loc.distanceTo(merchantLoc)
        if (meters < 1000) "${meters.toInt()}m" else "${"%.1f".format(meters / 1000)} km"
    }

    data class ScheduleInfo(val isOpen: Boolean, val label: String)

    val strNoSchedule = stringResource(R.string.feed_no_schedule)
    val strOpenCloses = stringResource(R.string.feed_open_closes)
    val strOpensAt = stringResource(R.string.feed_opens_at)
    val strOpensTomorrow = stringResource(R.string.feed_opens_tomorrow)
    val strOpensDay = stringResource(R.string.feed_opens_day)
    val strClosed = stringResource(R.string.feed_closed)
    val dayLabels = listOf(
        stringResource(R.string.feed_day_sun),
        stringResource(R.string.feed_day_mon),
        stringResource(R.string.feed_day_tue),
        stringResource(R.string.feed_day_wed),
        stringResource(R.string.feed_day_thu),
        stringResource(R.string.feed_day_fri),
        stringResource(R.string.feed_day_sat)
    )

    val scheduleInfo = remember(merchant, strNoSchedule, strOpenCloses, strOpensAt, strOpensTomorrow, strOpensDay, strClosed) {
        val schedule = merchant?.schedule
        if (schedule.isNullOrEmpty()) return@remember ScheduleInfo(false, strNoSchedule)
        val weekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val keys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val todayKey = keys[weekday - 1]
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val now = fmt.format(Date())

        val todayEntry = schedule.firstOrNull { it.day == todayKey }
        if (todayEntry != null) {
            if (now >= todayEntry.open && now <= todayEntry.close) {
                return@remember ScheduleInfo(true, "$strOpenCloses ${formatHHmm(todayEntry.close)}")
            } else if (now < todayEntry.open) {
                return@remember ScheduleInfo(false, "$strOpensAt ${formatHHmm(todayEntry.open)}")
            }
        }
        // Closed today or past closing — find next opening
        for (offset in 1..7) {
            val nextIdx = (weekday - 1 + offset) % 7
            val nextDay = keys[nextIdx]
            val entry = schedule.firstOrNull { it.day == nextDay }
            if (entry != null) {
                return@remember if (offset == 1) {
                    ScheduleInfo(false, "$strOpensTomorrow ${formatHHmm(entry.open)}")
                } else {
                    ScheduleInfo(false, "$strOpensDay ${dayLabels[nextIdx]} ${formatHHmm(entry.open)}")
                }
            }
        }
        ScheduleInfo(false, strClosed)
    }

    Card(
        onClick = { onNavigateToDetail(menu.id) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── IMAGE ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                AsyncImage(
                    model = menu.photoUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // TOP-RIGHT: offer type + price chip
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    menu.offerType?.takeIf { it.isNotEmpty() }?.let { offerType ->
                        Surface(
                            // Color unificado para todas las pastillas de tipo de oferta.
                            color = Color(0xFFE85D3F),
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                offerTypeLabel(offerType),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            "${"%.2f".format(menu.priceTotal)}€",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1A2E),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // ── INFO ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        merchant?.name ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Chip row: status · distance · cuisine (wraps, never hidden)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InlineChip(
                        icon = Icons.Default.Schedule,
                        label = scheduleInfo.label,
                        foreground = if (scheduleInfo.isOpen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        background = if (scheduleInfo.isOpen) Color(0xFF22C55E) else MaterialTheme.colorScheme.surfaceVariant
                    )
                    distanceText?.let { dist ->
                        InlineChip(
                            icon = Icons.Default.LocationOn,
                            label = dist,
                            foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    merchant?.cuisineTypes?.firstOrNull()?.let { cuisine ->
                        InlineChip(
                            icon = Icons.Default.Restaurant,
                            label = cuisine,
                            foreground = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InlineChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    foreground: Color,
    background: Color
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = foreground
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = foreground,
                maxLines = 1
            )
        }
    }
}

// MARK: - Active Filters

data class ActiveFilters(
    val cuisine: String? = null,
    val maxPrice: Double? = null,
    val maxDistanceKm: Double? = null,
    val onlyFavorites: Boolean = false,
    val onlyOpen: Boolean = false,
    val offerType: String? = null
) {
    val isActive get() = cuisine != null || (maxPrice != null && maxPrice < 100) || maxDistanceKm != null || onlyFavorites || onlyOpen || offerType != null
}

/** Valores canónicos de offerType tal como se guardan en Firestore. */
object OfferTypes {
    const val MENU_DEL_DIA = "Menú del día"
    const val PLATO_DEL_DIA = "Plato del día"
    const val OFERTA_DEL_DIA = "Oferta del día"
    const val OFERTA_PERMANENTE = "Oferta permanente"
    val ALL = listOf(MENU_DEL_DIA, PLATO_DEL_DIA, OFERTA_DEL_DIA, OFERTA_PERMANENTE)
}

/**
 * Devuelve la etiqueta traducida para un valor canónico de offerType.
 * Si el valor es desconocido (datos legacy en otro idioma), lo devuelve tal cual.
 */
@Composable
fun offerTypeLabel(value: String): String = when (value) {
    OfferTypes.MENU_DEL_DIA -> stringResource(R.string.offer_type_menu)
    OfferTypes.PLATO_DEL_DIA -> stringResource(R.string.offer_type_plato)
    OfferTypes.OFERTA_DEL_DIA -> stringResource(R.string.offer_type_oferta)
    OfferTypes.OFERTA_PERMANENTE -> stringResource(R.string.offer_type_permanente)
    else -> value
}

// MARK: - Filter Sheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initialFilters: ActiveFilters,
    onDismiss: () -> Unit,
    onApply: (String?, Double?, Double?, Boolean, Boolean, String?) -> Unit,
    currencyPreference: String? = null,
    formatConverted: (Double, String) -> String? = { _, _ -> null },
) {
    var selectedCuisine by remember { mutableStateOf(initialFilters.cuisine) }
    var maxPrice by remember { mutableStateOf(initialFilters.maxPrice?.toFloat() ?: 30f) }
    var maxDistanceKm by remember { mutableStateOf(initialFilters.maxDistanceKm) }
    var onlyFavorites by remember { mutableStateOf(initialFilters.onlyFavorites) }
    var onlyOpen by remember { mutableStateOf(initialFilters.onlyOpen) }
    var offerType by remember { mutableStateOf(initialFilters.offerType) }
    var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }

    val distanceOptions: List<Pair<String, Double?>> = listOf(
        stringResource(R.string.filter_any) to null,
        stringResource(R.string.filter_1km) to 1.0,
        stringResource(R.string.filter_2km) to 2.0,
        stringResource(R.string.filter_5km) to 5.0,
        stringResource(R.string.filter_10km) to 10.0,
        stringResource(R.string.filter_25km) to 25.0,
    )

    LaunchedEffect(Unit) {
        cuisines = try {
            FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
                Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                TextButton(onClick = {
                    selectedCuisine = null
                    maxPrice = 30f
                    maxDistanceKm = null
                    onlyFavorites = false
                    onlyOpen = false
                    offerType = null
                }) { Text(stringResource(R.string.filter_clear)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter_status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.filter_open_now), modifier = Modifier.weight(1f))
                    Switch(checked = onlyOpen, onCheckedChange = { onlyOpen = it })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.filter_max_distance), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    distanceOptions.forEach { (label, value) ->
                        FilterChip(
                            selected = maxDistanceKm == value,
                            onClick = { maxDistanceKm = value },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.filter_offer_type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val offerTypeLabels = listOf(
                    OfferTypes.MENU_DEL_DIA to stringResource(R.string.offer_type_menu),
                    OfferTypes.PLATO_DEL_DIA to stringResource(R.string.offer_type_plato),
                    OfferTypes.OFERTA_DEL_DIA to stringResource(R.string.offer_type_oferta),
                    OfferTypes.OFERTA_PERMANENTE to stringResource(R.string.offer_type_permanente),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    offerTypeLabels.forEach { (value, label) ->
                        FilterChip(
                            selected = offerType == value,
                            onClick = { offerType = if (offerType == value) null else value },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.filter_cuisine_type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cuisines.forEach { cuisine ->
                        FilterChip(
                            selected = selectedCuisine == cuisine,
                            onClick = { selectedCuisine = if (selectedCuisine == cuisine) null else cuisine },
                            label = { Text(cuisine, maxLines = 1, softWrap = false) },
                            colors = com.sozolab.zampa.ui.theme.brandFilterChipColors()
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(stringResource(R.string.filter_max_price), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${stringResource(R.string.filter_up_to)} ${maxPrice.toInt()} €", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        val prefCode = currencyPreference ?: "EUR"
                        if (prefCode != "EUR") {
                            val converted = remember(maxPrice.toInt(), prefCode) {
                                formatConverted(maxPrice.toInt().toDouble(), prefCode)
                            }
                            converted?.let {
                                Text(
                                    "~$it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Slider(
                    value = maxPrice,
                    onValueChange = { maxPrice = it },
                    valueRange = 5f..100f,
                    steps = 18 // 19 posiciones de 5 en 5: 5, 10, 15, ... 100
                )
                Row {
                    Text(stringResource(R.string.filter_min_price_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.filter_max_price_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = { onApply(selectedCuisine, maxPrice.toDouble(), maxDistanceKm, onlyFavorites, onlyOpen, offerType) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.filter_show_results))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatAddress(formatted: String): String {
    return formatted.split(",").firstOrNull()?.trim() ?: formatted
}

/**
 * Normaliza el string de horario a "HH:mm" (24h) independientemente del
 * formato de entrada: "10:00", "10:00:00", "10:00Z", "2026-04-11T10:00:00Z".
 */
private fun formatHHmm(time: String): String {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.takeLast(2)?.toIntOrNull() ?: return time
    val m = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0
    return "%02d:%02d".format(h, m)
}
