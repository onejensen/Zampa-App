package com.sozolab.zampa.ui.feed

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.sozolab.zampa.R
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.Menu
import com.sozolab.zampa.data.model.Merchant
import com.sozolab.zampa.data.model.ScheduleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Perfil público de un restaurante/comercio: cover, info (horario, dirección,
 * teléfono), acciones (llamar, cómo llegar) y lista de ofertas activas.
 * Reutiliza `getMerchantProfile` + `getMenusByMerchant`.
 */
@HiltViewModel
class MerchantProfileViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _merchant = MutableStateFlow<Merchant?>(null)
    val merchant: StateFlow<Merchant?> = _merchant

    private val _offers = MutableStateFlow<List<Menu>>(emptyList())
    val offers: StateFlow<List<Menu>> = _offers

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private var currentMerchantId: String? = null

    fun load(merchantId: String) {
        currentMerchantId = merchantId
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val merchantDef = async { firebaseService.getMerchantProfile(merchantId) }
                val offersDef = async { firebaseService.getMenusByMerchant(merchantId) }
                val favDef = async {
                    try { firebaseService.isFavorite(merchantId) } catch (_: Exception) { false }
                }
                _merchant.value = merchantDef.await()
                _offers.value = offersDef.await().filter { it.isToday }
                _isFavorite.value = favDef.await()
            } catch (_: Exception) {
                // silent
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Optimistic update: el UI se actualiza inmediatamente, se revierte si falla. */
    fun toggleFavorite() {
        val merchantId = currentMerchantId ?: return
        val previous = _isFavorite.value
        _isFavorite.value = !previous
        viewModelScope.launch {
            try {
                _isFavorite.value = firebaseService.toggleFavorite(merchantId)
            } catch (_: Exception) {
                _isFavorite.value = previous
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileScreen(
    merchantId: String,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: MerchantProfileViewModel = hiltViewModel()
) {
    val merchant by viewModel.merchant.collectAsState()
    val offers by viewModel.offers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(merchantId) { viewModel.load(merchantId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        merchant?.name ?: stringResource(R.string.merchant_profile_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = com.sozolab.zampa.ui.theme.brandTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Cover
            if (!merchant?.coverPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = merchant?.coverPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.logo_zampa),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                        alpha = 0.35f,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Nombre + cocinas
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        merchant?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    val cuisines = merchant?.cuisineTypes ?: emptyList()
                    if (cuisines.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            cuisines.forEach { cuisine ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        cuisine,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Descripción
                merchant?.shortDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(desc, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                }

                // Info rows
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    merchant?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        InfoRow(Icons.Default.Phone, phone)
                    }
                    val addressText = merchant?.addressText ?: merchant?.address?.formatted
                    addressText?.let { InfoRow(Icons.Default.LocationOn, it) }
                    merchant?.schedule?.takeIf { it.isNotEmpty() }?.let { schedule ->
                        ScheduleBlock(schedule)
                    }
                }

                // Mini mapa (Google Maps Compose, no interactivo)
                merchant?.address?.takeIf { it.lat != 0.0 || it.lng != 0.0 }?.let { addr ->
                    val position = LatLng(addr.lat, addr.lng)
                    val cameraState = rememberCameraPositionState {
                        this.position = CameraPosition.fromLatLngZoom(position, 15f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                rotationGesturesEnabled = false,
                                tiltGesturesEnabled = false,
                                mapToolbarEnabled = false,
                                myLocationButtonEnabled = false,
                            ),
                        ) {
                            com.google.maps.android.compose.Marker(
                                state = com.google.maps.android.compose.rememberMarkerState(position = position),
                                title = merchant?.name ?: "",
                            )
                        }
                    }
                }

                // Botones acción — fila 1: Llamar + Cómo ir
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    merchant?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        MerchantProfileActionBtn(
                            icon = Icons.Default.Phone,
                            label = stringResource(R.string.detail_call),
                            modifier = Modifier.weight(1f)
                        ) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
                            context.startActivity(intent)
                        }
                    }
                    merchant?.address?.let { addr ->
                        MerchantProfileActionBtn(
                            icon = Icons.Default.Map,
                            label = stringResource(R.string.detail_directions),
                            modifier = Modifier.weight(1f)
                        ) {
                            val hasCoords = addr.lat != 0.0 || addr.lng != 0.0
                            val uri = if (hasCoords) {
                                Uri.parse("geo:${addr.lat},${addr.lng}?q=${addr.lat},${addr.lng}(${Uri.encode(merchant?.name ?: "")})")
                            } else {
                                Uri.parse("geo:0,0?q=${Uri.encode(addr.formatted)}")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Botones acción — fila 2: Favorito + Compartir (siempre disponibles
                // aunque el comercio no tenga oferta del día).
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MerchantProfileActionBtn(
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = stringResource(R.string.detail_favorite),
                        modifier = Modifier.weight(1f),
                        iconTint = if (isFavorite) androidx.compose.ui.graphics.Color.Red else null
                    ) { viewModel.toggleFavorite() }
                    val shareText = stringResource(R.string.merchant_share_text)
                    val merchantName = merchant?.name ?: ""
                    MerchantProfileActionBtn(
                        icon = Icons.Default.Share,
                        label = stringResource(R.string.detail_share),
                        modifier = Modifier.weight(1f)
                    ) {
                        val url = "https://www.getzampa.com/r/$merchantId"
                        val body = "$shareText $merchantName: $url"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, body)
                            putExtra(Intent.EXTRA_SUBJECT, merchantName)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }

                HorizontalDivider()

                // Ofertas
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.merchant_profile_offers_header),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    when {
                        isLoading -> {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        offers.isEmpty() -> {
                            Text(
                                stringResource(R.string.merchant_profile_no_offers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            )
                        }
                        else -> {
                            offers.forEach { offer ->
                                OfferMiniCard(offer) { onNavigateToDetail(offer.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

@Composable
private fun MerchantProfileActionBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconTint ?: androidx.compose.ui.graphics.Color.Unspecified
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OfferMiniCard(menu: Menu, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = menu.photoUrls.firstOrNull(),
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    menu.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                menu.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    "${"%.2f".format(menu.priceTotal)} €",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Bloque de horario semanal Lun-Dom con HOY resaltado.
 * Los días sin entry se muestran como "Cerrado".
 */
@Composable
private fun ScheduleBlock(schedule: List<ScheduleEntry>) {
    val locale = java.util.Locale.getDefault()
    val dayNames = java.text.DateFormatSymbols.getInstance(locale).weekdays
    val todayIdx = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0=sunday
    val keys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    // Orden Lun→Dom (España). Idx 1..6, luego 0.
    val displayOrder = listOf(1, 2, 3, 4, 5, 6, 0)
    val closedLabel = stringResource(R.string.schedule_closed)

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Default.AccessTime,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            displayOrder.forEach { idx ->
                val key = keys[idx]
                val entry = schedule.firstOrNull { it.day == key }
                val name = dayNames[idx + 1].replaceFirstChar { it.uppercase(locale) } // Calendar uses 1=Sun
                val isToday = idx == todayIdx
                val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
                val color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    entry == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        fontWeight = weight,
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (entry != null) "${entry.open} – ${entry.close}" else closedLabel,
                        fontWeight = weight,
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
