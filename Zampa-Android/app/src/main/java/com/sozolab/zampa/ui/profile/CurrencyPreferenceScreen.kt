package com.sozolab.zampa.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CurrencyOption(
    val code: String,
    val flag: String,
    val name: String,
    val symbol: String,
)

private val options = listOf(
    CurrencyOption("EUR", "🇪🇺", "Euro",                    "€"),
    CurrencyOption("USD", "🇺🇸", "Dólar estadounidense",    "$"),
    CurrencyOption("GBP", "🇬🇧", "Libra esterlina",          "£"),
    CurrencyOption("JPY", "🇯🇵", "Yen japonés",              "¥"),
    CurrencyOption("CHF", "🇨🇭", "Franco suizo",             "CHF"),
    CurrencyOption("SEK", "🇸🇪", "Corona sueca",             "kr"),
    CurrencyOption("NOK", "🇳🇴", "Corona noruega",           "kr"),
    CurrencyOption("DKK", "🇩🇰", "Corona danesa",            "kr"),
    CurrencyOption("CAD", "🇨🇦", "Dólar canadiense",         "C$"),
    CurrencyOption("AUD", "🇦🇺", "Dólar australiano",        "A$"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPreferenceScreen(
    currentCode: String,
    onSelect: (code: String, onError: (String) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moneda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            errorMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            items(options) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = pendingCode == null) {
                            if (option.code == currentCode) return@clickable
                            pendingCode = option.code
                            errorMessage = null
                            onSelect(option.code) { err ->
                                pendingCode = null
                                errorMessage = err
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.flag, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            option.code,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            option.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        option.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    when {
                        pendingCode == option.code -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        option.code == currentCode -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    text = "Los precios siempre se cobran en euros. Esta opción sólo cambia cómo se muestran en la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }

    // Cuando el current code cambia tras un select exitoso, volvemos atrás.
    LaunchedEffect(currentCode) {
        if (pendingCode != null && pendingCode == currentCode) {
            pendingCode = null
            onBack()
        }
    }
}
