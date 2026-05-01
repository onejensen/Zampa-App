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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sozolab.zampa.data.model.DietaryInfo
import com.sozolab.zampa.data.model.Menu
import androidx.compose.foundation.isSystemInDarkTheme
import com.sozolab.zampa.ui.theme.ChipBackground
import com.sozolab.zampa.ui.theme.ChipBackgroundDark

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val menus by viewModel.menus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingMenu by remember { mutableStateOf<Menu?>(null) }

    var isSelecting by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header simple — paridad iOS (`navigationTitle("Panel Restaurante")`).
        // No es un TopAppBar con actions, sólo el título centrado.
        Text(
            "Panel Restaurante",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── STATS GRID ──────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            icon = Icons.Default.Visibility,
                            title = "Vistas hoy",
                            value = "${todayStats.impressions}",
                            iconColor = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.TouchApp,
                            title = "Clics hoy",
                            value = "${todayStats.clicks}",
                            iconColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            icon = Icons.Default.Favorite,
                            title = "Favoritos",
                            value = "${todayStats.favorites}",
                            iconColor = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.RestaurantMenu,
                            title = "Menús activos",
                            value = "${menus.count { it.isToday }}",
                            iconColor = Color(0xFF22C55E),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── BIG PUBLISH BUTTON ──────────────────────────────────────
            // Espejo del iOS: relleno naranja brand, texto blanco, glow naranja
            // alrededor (shadow coloreada). `ambientColor`/`spotColor` requieren
            // API 28+; en API 26-27 la sombra es gris default pero el botón se
            // sigue viendo correcto.
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showCreateSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = Color.White
                        )
                        Text(
                            "PUBLICAR OFERTA",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            // ── "MIS OFERTAS" HEADER ────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mis ofertas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    if (menus.isNotEmpty()) {
                        TextButton(onClick = {
                            isSelecting = !isSelecting
                            if (!isSelecting) selectedIds.clear()
                        }) {
                            Text(if (isSelecting) "Listo" else "Editar")
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
                                "Aún no has publicado ningún menú",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showCreateSheet = true }) {
                                Text("Publicar mi primer menú")
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
                            Text("Eliminar seleccionados (${selectedIds.size})")
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
                    Text("Eliminar seleccionados (${selectedIds.size})")
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
            title = { Text("Eliminar ${selectedIds.size} menú(s)") },
            text = { Text("Esta acción eliminará los menús seleccionados. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMenus(selectedIds.toList())
                    selectedIds.clear()
                    isSelecting = false
                    showBulkDeleteConfirm = false
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

// Chip colors con contraste sobre el contenedor (InputBackground) en ambos modos.
// Light: chip muy claro sobre gris claro. Dark: chip más claro que el fondo del form.
@Composable
private fun zampaChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = if (isSystemInDarkTheme()) ChipBackgroundDark else ChipBackground,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurface,
)

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
    isPro: Boolean = false,
) {
    val offerTypes = listOf("Menú del día", "Plato del día", "Oferta permanente")
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tipo de oferta", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offerTypes.forEach { type ->
                    val isPermanent = type == "Oferta permanente"
                    val locked = isPermanent && !isPro
                    val isSelected = offerType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { if (!locked) onOfferTypeChange(if (isSelected) null else type) },
                        enabled = !locked,
                        colors = zampaChipColors(),
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (locked) Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text(type)
                            }
                        }
                    )
                }
            }
            if (!isPro) {
                Text(
                    "«Oferta permanente» requiere Plan Pro — no caduca a las 24h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Horario de la oferta
            Text("Horario de la oferta", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("lunch" to "Mediodía", "dinner" to "Noche", "both" to "Ambos").forEach { (value, label) ->
                    FilterChip(
                        selected = serviceTime == value,
                        onClick = { onServiceTimeChange(value) },
                        colors = zampaChipColors(),
                        label = { Text(label) }
                    )
                }
            }

            Text("Incluye", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = includesDrink,   onClick = { onDrinkChange(!includesDrink) },     colors = zampaChipColors(), label = { Text("Bebida") })
                FilterChip(selected = includesDessert, onClick = { onDessertChange(!includesDessert) }, colors = zampaChipColors(), label = { Text("Postre") })
                FilterChip(selected = includesCoffee,  onClick = { onCoffeeChange(!includesCoffee) },   colors = zampaChipColors(), label = { Text("Café") })
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
            Text("Información dietética", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))

            // Diet
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Dieta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = isVegetarian, onClick = { onVegetarianChange(!isVegetarian) }, colors = zampaChipColors(), label = { Text("Vegetariano") })
                    FilterChip(selected = isVegan, onClick = { onVeganChange(!isVegan) }, colors = zampaChipColors(), label = { Text("Vegano") })
                }
            }

            // Protein
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Proteína principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = hasMeat, onClick = { onMeatChange(!hasMeat) }, colors = zampaChipColors(), label = { Text("Carne") })
                    FilterChip(selected = hasFish, onClick = { onFishChange(!hasFish) }, colors = zampaChipColors(), label = { Text("Pescado/Marisco") })
                }
            }

            // Allergens
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Alérgenos presentes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = hasGluten,  onClick = { onGlutenChange(!hasGluten) },   colors = zampaChipColors(), label = { Text("Gluten") })
                    FilterChip(selected = hasLactose, onClick = { onLactoseChange(!hasLactose) }, colors = zampaChipColors(), label = { Text("Lácteos") })
                    FilterChip(selected = hasNuts,    onClick = { onNutsChange(!hasNuts) },       colors = zampaChipColors(), label = { Text("Frutos secos") })
                    FilterChip(selected = hasEgg,     onClick = { onEggChange(!hasEgg) },         colors = zampaChipColors(), label = { Text("Huevo") })
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
                    if (!menu.isToday) {
                        Text("Pasada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text("¿Eliminar menú?") },
            text = { Text("¿Estás seguro de que quieres eliminar esta oferta? Los clientes ya no podrán verla.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
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
    val isPermanentMenu = menu.isPermanent
    val selectedDays = remember { mutableStateOf(menu.recurringDays?.toSet() ?: emptySet()) }
    val occupiedDays by viewModel.occupiedDays.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val updateSuccess by viewModel.updateSuccess.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            imageData = context.contentResolver.openInputStream(it)?.readBytes()
        }
    }

    LaunchedEffect(updateSuccess) {
        if (updateSuccess) {
            viewModel.resetUpdateSuccess()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        availableTags = try {
            com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
        viewModel.loadOccupiedDays(excludingMenuId = menu.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Editar menú",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText, onValueChange = { priceText = it }, label = { Text("Precio (€)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text("Tipo de cocina", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        colors = zampaChipColors(),
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
                isPro = isPremium,
            )
            Spacer(Modifier.height(12.dp))

            // Recurring days picker — only for permanent menus
            if (isPermanentMenu) {
                Spacer(Modifier.height(12.dp))
                RecurringDaysPicker(
                    occupiedDays = occupiedDays,
                    selectedDays = selectedDays.value,
                    onSelectionChange = { selectedDays.value = it }
                )
            }

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
                Text("Cambiar foto")
            }
            Spacer(Modifier.height(20.dp))

            val price = priceText.toDoubleOrNull() ?: 0.0
            val dietary = DietaryInfo(dietIsVegetarian, dietIsVegan, dietHasMeat, dietHasFish, dietHasGluten, dietHasLactose, dietHasNuts, dietHasEgg)
            Button(
                onClick = {
                    viewModel.updateMenu(
                        menu.id, title, description, price, selectedTags.toList(), imageData,
                        dietary, offerType, inclDrink, inclDessert, inclCoffee,
                        recurringDays = if (isPermanentMenu) selectedDays.value.sorted() else null
                    )
                },
                enabled = !isLoading && title.isNotBlank() && price > 0
                    && (!isPermanentMenu || selectedDays.value.isNotEmpty()),
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
                    Text("Guardar cambios")
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Eliminar menú")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar menú?") },
            text = { Text("¿Estás seguro de que quieres eliminar esta oferta? Los clientes ya no podrán verla.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMenu(menu.id)
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
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
    var selectedDays by remember { mutableStateOf(emptySet<Int>()) }
    val occupiedDays by viewModel.occupiedDays.collectAsState()

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
            title = { Text("Foto del menú") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cámara") }
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Galería") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Cancelar") }
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
        viewModel.loadOccupiedDays()
        availableTags = try {
            com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Nuevo menú",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
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
                        Text("Añadir foto del menú", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2, maxLines = 4
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText, onValueChange = { priceText = it }, label = { Text("Precio (€)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text("Tipo de cocina", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
                        },
                        colors = zampaChipColors(),
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
                isPro = isPremium,
            )
            Spacer(Modifier.height(12.dp))

            // Recurring days picker — only for permanent offers
            if (offerType == "Oferta permanente") {
                Spacer(Modifier.height(12.dp))
                RecurringDaysPicker(
                    occupiedDays = occupiedDays,
                    selectedDays = selectedDays,
                    onSelectionChange = { selectedDays = it }
                )
            }

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
                onClick = {
                    imageData?.let {
                        viewModel.createMenu(
                            title, description, price, it, tags, dietary,
                            offerType, inclDrink, inclDessert, inclCoffee, serviceTime,
                            isPermanent = offerType == "Oferta permanente",
                            recurringDays = if (offerType == "Oferta permanente") selectedDays.sorted() else null
                        )
                    }
                },
                enabled = !isLoading && title.isNotBlank() && price > 0 && imageData != null
                    && (offerType != "Oferta permanente" || selectedDays.isNotEmpty()),
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
                    Text("Publicar oferta")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
