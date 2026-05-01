package com.sozolab.zampa.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.sozolab.zampa.R
import com.sozolab.zampa.data.model.Menu
import com.sozolab.zampa.data.model.Merchant

/**
 * Representa un pin en el mapa: un merchant con al menos una oferta activa.
 * Varias ofertas del mismo merchant → un solo pin (agrupadas en `offers`).
 */
data class MerchantPin(
    val merchantId: String,
    val merchant: Merchant,
    val offers: List<Menu>,
    private val lat: Double,
    private val lng: Double,
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(lat, lng)
    override fun getTitle(): String = merchant.name
    override fun getSnippet(): String? = offers.firstOrNull()?.title
    override fun getZIndex(): Float = 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedMapView(
    menus: List<Menu>,
    merchantMap: Map<String, Merchant>,
    userLocation: android.location.Location?,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToMerchant: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Construimos pines a partir de TODOS los merchants en merchantMap (incluye
    // los que cargó `loadAllMerchants` además de los que vienen de `loadMenus`).
    // Cada pin lleva sus ofertas del día (lista vacía si no tiene). El estilo del
    // marker se decide en BrandMarker según si offers está vacío o no.
    val pins = remember(menus, merchantMap) {
        val offersByMerchant = menus.groupBy { it.businessId }
        merchantMap.values.mapNotNull { m ->
            val addr = m.address ?: return@mapNotNull null
            if (addr.lat == 0.0 && addr.lng == 0.0) return@mapNotNull null
            val merchantOffers = offersByMerchant[m.id] ?: emptyList()
            MerchantPin(m.id, m, merchantOffers, addr.lat, addr.lng)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        // Centrado inicial: ubicación del user si la hay, si no centro geográfico de España.
        val initial = userLocation?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(40.4168, -3.7038) // Madrid
        position = CameraPosition.fromLatLngZoom(initial, if (userLocation != null) 13f else 6f)
    }

    // Al cargar/actualizar pines, encuadrar para verlos todos.
    LaunchedEffect(pins) {
        if (pins.isEmpty()) return@LaunchedEffect
        if (pins.size == 1) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(pins.first().position, 15f))
            return@LaunchedEffect
        }
        val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder().apply {
            pins.forEach { include(it.position) }
            userLocation?.let { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 160))
    }

    var selectedPin by remember { mutableStateOf<MerchantPin?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (pins.isEmpty()) {
            EmptyMapOverlay()
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = userLocation != null,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = userLocation != null,
                mapToolbarEnabled = false,
            ),
        ) {
            Clustering(
                items = pins,
                onClusterItemClick = { pin ->
                    selectedPin = pin
                    true
                },
                clusterItemContent = { pin -> BrandMarker(hasOffers = pin.offers.isNotEmpty()) },
                clusterContent = { cluster -> BrandClusterMarker(count = cluster.size) },
            )
        }

        selectedPin?.let { pin ->
            ModalBottomSheet(
                onDismissRequest = { selectedPin = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MerchantPinSheet(
                    pin = pin,
                    userLocation = userLocation,
                    onClose = { selectedPin = null },
                    onViewOffer = { offerId ->
                        selectedPin = null
                        onNavigateToDetail(offerId)
                    },
                    onViewRestaurant = { merchantId ->
                        selectedPin = null
                        onNavigateToMerchant(merchantId)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyMapOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.map_no_locations),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MerchantPinSheet(
    pin: MerchantPin,
    userLocation: android.location.Location?,
    onClose: () -> Unit,
    onViewOffer: (String) -> Unit,
    onViewRestaurant: (String) -> Unit,
) {
    val primary = pin.offers.firstOrNull()
    val extraCount = (pin.offers.size - 1).coerceAtLeast(0)
    val distanceKm: Double? = userLocation?.let { user ->
        val m = android.location.Location("").apply {
            latitude = pin.position.latitude
            longitude = pin.position.longitude
        }
        user.distanceTo(m) / 1000.0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Foto: si hay oferta, foto de la oferta; si no, cover photo del comercio.
            AsyncImage(
                model = primary?.photoUrls?.firstOrNull() ?: pin.merchant.coverPhotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pin.merchant.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (primary != null) {
                    Text(
                        primary.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${"%.2f".format(primary.priceTotal)} €",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        distanceKm?.let {
                            Text(
                                "· ${"%.1f".format(it)} km",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Sin oferta hoy
                    Text(
                        stringResource(R.string.map_no_offer_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    distanceKm?.let {
                        Text(
                            "${"%.1f".format(it)} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (extraCount > 0) {
            Text(
                stringResource(R.string.map_more_offers, extraCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (primary != null) {
            Button(
                onClick = { onViewOffer(primary.id) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.map_view_offer))
            }
            OutlinedButton(
                onClick = { onViewRestaurant(pin.merchantId) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.map_view_restaurant))
            }
        } else {
            // Sin oferta: el CTA principal es el perfil del comercio (donde puede
            // favoritar / compartir / llamar).
            Button(
                onClick = { onViewRestaurant(pin.merchantId) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.map_view_restaurant))
            }
        }
    }
}

/**
 * Marker custom que muestra el logo de Zampa.
 * - `hasOffers == true` → círculo naranja brand (comercio con oferta hoy).
 * - `hasOffers == false` → círculo gris (comercio sin oferta hoy, todavía
 *   accesible para favoritar/compartir).
 */
@Composable
private fun BrandMarker(hasOffers: Boolean = true) {
    val bg = if (hasOffers) Color(0xFFFFAA1C) else Color(0xFF9E9E9E)
    Box(
        modifier = Modifier
            .size(if (hasOffers) 46.dp else 38.dp)
            .shadow(4.dp, CircleShape)
            .background(bg, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_zampa),
            contentDescription = null,
            modifier = Modifier.size(if (hasOffers) 28.dp else 22.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Marker de cluster (grupo de pines cercanos): más grande, con contador. */
@Composable
private fun BrandClusterMarker(count: Int) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(6.dp, CircleShape)
            .background(Color(0xFFFFAA1C), CircleShape)
            .border(3.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
