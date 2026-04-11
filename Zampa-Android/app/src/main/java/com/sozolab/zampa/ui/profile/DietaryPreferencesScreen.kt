package com.sozolab.zampa.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietaryPreferencesScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("dietary_prefs", android.content.Context.MODE_PRIVATE)
    }

    var isVegetarian by remember { mutableStateOf(prefs.getBoolean("isVegetarian", false)) }
    var isVegan by remember { mutableStateOf(prefs.getBoolean("isVegan", false)) }
    var isMeatFree by remember { mutableStateOf(prefs.getBoolean("isMeatFree", false)) }
    var isFishFree by remember { mutableStateOf(prefs.getBoolean("isFishFree", false)) }
    var isGlutenFree by remember { mutableStateOf(prefs.getBoolean("isGlutenFree", false)) }
    var isLactoseFree by remember { mutableStateOf(prefs.getBoolean("isLactoseFree", false)) }
    var isNutFree by remember { mutableStateOf(prefs.getBoolean("isNutFree", false)) }

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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Dietas
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Dietas",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PreferenceToggle(
                        title = "Vegetariano",
                        checked = isVegetarian,
                        onCheckedChange = { isVegetarian = it }
                    )
                    PreferenceToggle(
                        title = "Vegano",
                        checked = isVegan,
                        onCheckedChange = {
                            isVegan = it
                            if (it) isVegetarian = true
                        }
                    )
                }

                // Alérgenos e Intolerancias
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Alérgenos e Intolerancias",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PreferenceToggle(
                        title = "Sin Carne",
                        checked = isMeatFree,
                        onCheckedChange = { isMeatFree = it }
                    )
                    PreferenceToggle(
                        title = "Sin Pescado",
                        checked = isFishFree,
                        onCheckedChange = { isFishFree = it }
                    )
                    PreferenceToggle(
                        title = "Sin Gluten",
                        checked = isGlutenFree,
                        onCheckedChange = { isGlutenFree = it }
                    )
                    PreferenceToggle(
                        title = "Sin Lactosa",
                        checked = isLactoseFree,
                        onCheckedChange = { isLactoseFree = it }
                    )
                    PreferenceToggle(
                        title = "Sin Frutos Secos",
                        checked = isNutFree,
                        onCheckedChange = { isNutFree = it }
                    )
                }

                Text(
                    "Las ofertas que no sean compatibles con tus preferencias se ocultarán automáticamente en el feed. Las ofertas sin información dietética siempre se muestran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Save button
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = {
                        prefs.edit()
                            .putBoolean("isVegetarian", isVegetarian)
                            .putBoolean("isVegan", isVegan)
                            .putBoolean("isMeatFree", isMeatFree)
                            .putBoolean("isFishFree", isFishFree)
                            .putBoolean("isGlutenFree", isGlutenFree)
                            .putBoolean("isLactoseFree", isLactoseFree)
                            .putBoolean("isNutFree", isNutFree)
                            .apply()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("Guardar preferencias")
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
