package com.sozolab.zampa.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sozolab.zampa.data.FirebaseService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit = {}) {
    val firebaseService = remember { FirebaseService() }
    var calls by remember { mutableStateOf<List<String>>(emptyList()) }
    var directions by remember { mutableStateOf<List<String>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val history = firebaseService.getUserHistory()
            calls = history.filter { it["action"] == "call" }.mapNotNull { it["businessName"] as? String }
            directions = history.filter { it["action"] == "directions" }.mapNotNull { it["businessName"] as? String }
            val favList = firebaseService.getFavorites()
            val favNames = favList.mapNotNull { fav ->
                try {
                    firebaseService.getMerchantProfile(fav.businessId)?.name
                } catch (_: Exception) { null }
            }
            favorites = favNames
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        "Llamadas",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (calls.isEmpty()) {
                    item {
                        Text(
                            "No hay registros",
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    items(calls) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF4CAF50))
                            }
                        )
                    }
                }

                item {
                    Text(
                        "Como ir",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (directions.isEmpty()) {
                    item {
                        Text(
                            "No hay registros",
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    items(directions) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = Color(0xFF2196F3))
                            }
                        )
                    }
                }

                item {
                    Text(
                        "Favoritos",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (favorites.isEmpty()) {
                    item {
                        Text(
                            "No hay registros",
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    items(favorites) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF44336))
                            }
                        )
                    }
                }
            }
        }
    }
}
