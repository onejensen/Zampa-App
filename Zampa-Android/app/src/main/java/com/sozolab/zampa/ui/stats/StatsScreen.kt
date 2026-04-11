package com.sozolab.zampa.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Estadísticas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                TimeRangeSelector(
                    selectedDays = uiState.timeRangeDays,
                    onDaysSelected = { viewModel.setTimeRange(it) }
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.stats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No hay datos suficientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    SummaryCards(uiState.stats)
                }

                item {
                    MetricChart(
                        title = "Impresiones (Visibilidad)",
                        stats = uiState.stats,
                        valueSelector = { it.impressions.toFloat() },
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    InteractionBreakdown(uiState.stats)
                }
            }
        }
    }
}

@Composable
fun TimeRangeSelector(selectedDays: Int, onDaysSelected: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val options = listOf(7 to "7d", 14 to "14d", 30 to "30d")
        options.forEachIndexed { index, (days, label) ->
            SegmentedButton(
                selected = selectedDays == days,
                onClick = { onDaysSelected(days) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
fun SummaryCards(stats: List<DailyStat>) {
    val totalImpressions = stats.sumOf { it.impressions }
    val totalFavorites = stats.sumOf { it.favorites }
    val totalCalls = stats.sumOf { it.calls }
    val totalInteractions = stats.sumOf { it.calls + it.directions + it.shares }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(Modifier.weight(1f), "Impresiones", totalImpressions.toString(), Icons.Default.Visibility, MaterialTheme.colorScheme.primary)
            StatCard(Modifier.weight(1f), "Favoritos", totalFavorites.toString(), Icons.Default.Favorite, Color.Red)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(Modifier.weight(1f), "Interacciones", totalInteractions.toString(), Icons.Default.TouchApp, Color.Blue)
            StatCard(Modifier.weight(1f), "Llamadas", totalCalls.toString(), Icons.Default.Phone, Color(0xFF4CAF50))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricChart(title: String, stats: List<DailyStat>, valueSelector: (DailyStat) -> Float, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            val maxVal = stats.maxOf { valueSelector(it) }.takeIf { it > 0 } ?: 1f
            
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                stats.forEach { stat ->
                    val heightFactor = valueSelector(stat) / maxVal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFactor.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(color.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

@Composable
fun InteractionBreakdown(stats: List<DailyStat>) {
    val calls = stats.sumOf { it.calls }
    val directions = stats.sumOf { it.directions }
    val shares = stats.sumOf { it.shares }
    val total = (calls + directions + shares).coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Tipos de Interacción", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            InteractionRow("Llamadas", calls, Color(0xFF4CAF50), calls.toFloat() / total)
            Spacer(Modifier.height(12.dp))
            InteractionRow("Cómo llegar", directions, Color.Blue, directions.toFloat() / total)
            Spacer(Modifier.height(12.dp))
            InteractionRow("Compartir", shares, Color.Magenta, shares.toFloat() / total)
        }
    }
}

@Composable
fun InteractionRow(label: String, count: Int, color: Color, progress: Float) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(count.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}
