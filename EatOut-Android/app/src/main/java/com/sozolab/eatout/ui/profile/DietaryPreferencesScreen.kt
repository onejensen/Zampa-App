package com.sozolab.eatout.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DietaryPreference(
    val name: String,
    val icon: String,
    var isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietaryPreferencesScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val preferences = remember {
        mutableStateListOf(
            DietaryPreference("Vegetariano", "🥬"),
            DietaryPreference("Vegano", "🌱"),
            DietaryPreference("Sin Gluten", "🌾"),
            DietaryPreference("Sin Lactosa", "🥛"),
            DietaryPreference("Kosher", "✡️"),
            DietaryPreference("Halal", "☪️"),
            DietaryPreference("Bajo en Calorías", "🔥"),
            DietaryPreference("Keto", "🥑"),
            DietaryPreference("Mediterráneo", "🫒"),
            DietaryPreference("Sin Frutos Secos", "🥜"),
            DietaryPreference("Sin Mariscos", "🦐"),
            DietaryPreference("Orgánico", "🍃")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferencias Alimentarias") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Selecciona tus preferencias para personalizar las recomendaciones de menús.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(preferences.size) { index ->
                val pref = preferences[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pref.isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    onClick = {
                        preferences[index] = pref.copy(isSelected = !pref.isSelected)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(pref.icon, style = MaterialTheme.typography.titleLarge)
                            Text(
                                pref.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (pref.isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (pref.isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { /* TODO: Save to Firestore */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar Preferencias")
                }
            }
        }
    }
}
