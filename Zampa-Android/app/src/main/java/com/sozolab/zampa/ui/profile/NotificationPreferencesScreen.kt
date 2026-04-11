package com.sozolab.zampa.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.NotificationPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(NotificationPreferences()) }
    var isLoading by remember { mutableStateOf(true) }
    val firebaseService = remember { FirebaseService() }

    LaunchedEffect(Unit) {
        prefs = firebaseService.getNotificationPreferences()
        isLoading = false
    }

    fun save(updated: NotificationPreferences) {
        prefs = updated
        scope.launch {
            try { firebaseService.updateNotificationPreferences(updated) } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Spacer(Modifier.height(8.dp))

            // Section: Restaurantes favoritos
            Text(
                "Restaurantes favoritos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Nuevas ofertas de favoritos",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Recibe una notificación cuando un restaurante favorito publique un nuevo menú o plato del día",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = prefs.newMenuFromFavorites,
                        onCheckedChange = { save(prefs.copy(newMenuFromFavorites = it)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Section: Otras notificaciones
            Text(
                "Otras notificaciones",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Promociones y descuentos",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Ofertas especiales y descuentos exclusivos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = prefs.promotions,
                            onCheckedChange = { save(prefs.copy(promotions = it)) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Novedades de Zampa",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Actualizaciones de la app y nuevas funcionalidades",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = prefs.general,
                            onCheckedChange = { save(prefs.copy(general = it)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Pro info card
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF6B35).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFF6B35),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Zampa Pro",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF6B35)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Los restaurantes con suscripción Pro envían notificaciones automáticas a sus seguidores cada vez que publican una nueva oferta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
