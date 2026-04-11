package com.sozolab.zampa.ui.feed

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.Menu
import com.sozolab.zampa.data.model.Merchant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SortOption { DISTANCE, PRICE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
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

    var sortOption by remember { mutableStateOf(SortOption.DISTANCE) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var activeFilters by remember { mutableStateOf(ActiveFilters()) }
    var selectedCuisine by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val sortedMenus = remember(menus, sortOption, selectedCuisine, activeFilters.onlyOpen, merchantMap, userLocation) {
        var result = menus
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

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "BUENOS DÍAS"
            hour < 20 -> "BUENAS TARDES"
            else -> "BUENAS NOCHES"
        }
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
                    if (greeting == "BUENAS NOCHES") "¿Dónde cenamos hoy?" else "¿Qué comemos hoy?",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onNavigateToProfile) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Perfil",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
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
                if (sortOption == SortOption.PRICE) "Ofertas por precio" else "Ofertas cerca de ti",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            if (menus.isNotEmpty()) {
                Text(
                    "${sortedMenus.size} encontrados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── SORT + FILTER ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SortPill(
                title = "Distancia",
                icon = Icons.Default.LocationOn,
                isSelected = sortOption == SortOption.DISTANCE,
                onClick = { sortOption = SortOption.DISTANCE }
            )
            SortPill(
                title = "Precio",
                icon = Icons.Default.AttachMoney,
                isSelected = sortOption == SortOption.PRICE,
                onClick = { sortOption = SortOption.PRICE }
            )
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filtros", tint = MaterialTheme.colorScheme.onSurface)
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
                    "Sin conexión",
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
                    Text("Reintentar")
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
                    "No encontramos menús con estos filtros",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeFilters.isActive) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        activeFilters = ActiveFilters()
                        selectedCuisine = null
                        viewModel.applyFilters(null, null, null, false)
                    }) { Text("Limpiar filtros") }
                }
            }
        } else {
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
                        userLocation = userLocation
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

    if (showFilterSheet) {
        FilterSheet(
            initialFilters = activeFilters,
            onDismiss = { showFilterSheet = false },
            onApply = { cuisine, price, distanceKm, favOnly, openOnly ->
                activeFilters = ActiveFilters(cuisine, price, distanceKm, favOnly, openOnly)
                selectedCuisine = cuisine
                viewModel.applyFilters(cuisine, price, distanceKm, favOnly)
                showFilterSheet = false
            }
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

@Composable
fun MenuCard(
    menu: Menu,
    onNavigateToDetail: (String) -> Unit,
    userLocation: android.location.Location? = null,
    onMerchantLoaded: (String, Merchant) -> Unit = { _, _ -> }
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

    val scheduleInfo = remember(merchant) {
        val schedule = merchant?.schedule
        if (schedule.isNullOrEmpty()) return@remember ScheduleInfo(false, "Sin horario")
        val weekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val keys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
        val dayLabels = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        val todayKey = keys[weekday - 1]
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val now = fmt.format(Date())

        val todayEntry = schedule.firstOrNull { it.day == todayKey }
        if (todayEntry != null) {
            if (now >= todayEntry.open && now <= todayEntry.close) {
                return@remember ScheduleInfo(true, "Abierto · Cierra ${formatHHmm(todayEntry.close)}")
            } else if (now < todayEntry.open) {
                return@remember ScheduleInfo(false, "Abre a las ${formatHHmm(todayEntry.open)}")
            }
        }
        // Closed today or past closing — find next opening
        for (offset in 1..7) {
            val nextIdx = (weekday - 1 + offset) % 7
            val nextDay = keys[nextIdx]
            val entry = schedule.firstOrNull { it.day == nextDay }
            if (entry != null) {
                return@remember if (offset == 1) {
                    ScheduleInfo(false, "Abre mañana ${formatHHmm(entry.open)}")
                } else {
                    ScheduleInfo(false, "Abre ${dayLabels[nextIdx]} ${formatHHmm(entry.open)}")
                }
            }
        }
        ScheduleInfo(false, "Cerrado")
    }

    Card(
        onClick = { onNavigateToDetail(menu.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── IMAGE ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                AsyncImage(
                    model = menu.photoUrls.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                startY = 100f
                            )
                        )
                )

                // TOP-LEFT: Destacado badge
                if (menu.isMerchantPro == true || merchant?.planTier == "pro") {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        color = Color(0xFFFF6B35),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                            Text(
                                "Destacado",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // TOP-RIGHT: Offer type + Price badge
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val offerType = menu.offerType
                    if (!offerType.isNullOrEmpty()) {
                        Surface(
                            color = when (offerType) {
                                "Menú del día" -> Color(0xFFFF6B35)
                                "Plato del día" -> Color(0xFF3B82F6)
                                else -> Color(0xFF9333EA)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                offerType,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${"%.2f".format(menu.priceTotal)}€",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // BOTTOM-LEFT: Distance chip
                distanceText?.let { dist ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = Color.White
                            )
                            Text(dist, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }

                // BOTTOM-RIGHT: Open status chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    color = if (scheduleInfo.isOpen) Color(0xFF22C55E) else Color(0xFF6B7280).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        scheduleInfo.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ── INFO ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            merchant?.name ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        merchant?.cuisineTypes?.firstOrNull()?.let { cuisine ->
                            Text(
                                cuisine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Ver oferta →",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// MARK: - Active Filters

data class ActiveFilters(
    val cuisine: String? = null,
    val maxPrice: Double? = null,
    val maxDistanceKm: Double? = null,
    val onlyFavorites: Boolean = false,
    val onlyOpen: Boolean = false
) {
    val isActive get() = cuisine != null || (maxPrice != null && maxPrice < 100) || maxDistanceKm != null || onlyFavorites || onlyOpen
}

// MARK: - Filter Sheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    initialFilters: ActiveFilters,
    onDismiss: () -> Unit,
    onApply: (String?, Double?, Double?, Boolean, Boolean) -> Unit
) {
    var selectedCuisine by remember { mutableStateOf(initialFilters.cuisine) }
    var maxPrice by remember { mutableStateOf(initialFilters.maxPrice?.toFloat() ?: 30f) }
    var maxDistanceKm by remember { mutableStateOf(initialFilters.maxDistanceKm) }
    var onlyFavorites by remember { mutableStateOf(initialFilters.onlyFavorites) }
    var onlyOpen by remember { mutableStateOf(initialFilters.onlyOpen) }
    var cuisines by remember { mutableStateOf<List<String>>(emptyList()) }

    val distanceOptions: List<Pair<String, Double?>> = listOf(
        "Cualquiera" to null,
        "1 km" to 1.0,
        "2 km" to 2.0,
        "5 km" to 5.0,
        "10 km" to 10.0,
        "25 km" to 25.0,
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
                Text("Filtrar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selectedCuisine = null
                    maxPrice = 30f
                    maxDistanceKm = null
                    onlyFavorites = false
                    onlyOpen = false
                }) { Text("Limpiar") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Solo abiertos ahora", modifier = Modifier.weight(1f))
                    Switch(checked = onlyOpen, onCheckedChange = { onlyOpen = it })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mis favoritos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Solo favoritos", modifier = Modifier.weight(1f))
                    Switch(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tipo de cocina", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cuisines.forEach { cuisine ->
                        FilterChip(
                            selected = selectedCuisine == cuisine,
                            onClick = { selectedCuisine = if (selectedCuisine == cuisine) null else cuisine },
                            label = { Text(cuisine, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Precio máximo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Hasta ${maxPrice.toInt()} €", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxPrice,
                    onValueChange = { maxPrice = it },
                    valueRange = 5f..100f,
                    steps = 18 // 19 posiciones de 5 en 5: 5, 10, 15, ... 100
                )
                Row {
                    Text("5€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("100€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Distancia máxima", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    distanceOptions.forEach { (label, value) ->
                        FilterChip(
                            selected = maxDistanceKm == value,
                            onClick = { maxDistanceKm = value },
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }

            Button(
                onClick = { onApply(selectedCuisine, maxPrice.toDouble(), maxDistanceKm, onlyFavorites, onlyOpen) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mostrar resultados")
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
