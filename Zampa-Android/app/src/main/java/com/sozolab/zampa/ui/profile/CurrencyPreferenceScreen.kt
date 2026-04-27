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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.R

private data class CurrencyOption(
    val code: String,
    val flag: String,
    val nameResId: Int,
    val symbol: String,
)

private val options = listOf(
    CurrencyOption("EUR", "🇪🇺", R.string.currency_eur,  "€"),
    CurrencyOption("USD", "🇺🇸", R.string.currency_usd,  "$"),
    CurrencyOption("GBP", "🇬🇧", R.string.currency_gbp,  "£"),
    CurrencyOption("JPY", "🇯🇵", R.string.currency_jpy,  "¥"),
    CurrencyOption("CHF", "🇨🇭", R.string.currency_chf,  "CHF"),
    CurrencyOption("SEK", "🇸🇪", R.string.currency_sek,  "kr"),
    CurrencyOption("NOK", "🇳🇴", R.string.currency_nok,  "kr"),
    CurrencyOption("DKK", "🇩🇰", R.string.currency_dkk,  "kr"),
    CurrencyOption("CAD", "🇨🇦", R.string.currency_cad,  "C$"),
    CurrencyOption("AUD", "🇦🇺", R.string.currency_aud,  "A$"),
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
                title = { Text(stringResource(R.string.currency_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = com.sozolab.zampa.ui.theme.brandTopAppBarColors()
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
                            stringResource(option.nameResId),
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
                                contentDescription = stringResource(R.string.currency_selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    text = stringResource(R.string.currency_footer),
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
