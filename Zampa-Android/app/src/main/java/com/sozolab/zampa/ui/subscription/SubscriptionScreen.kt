package com.sozolab.zampa.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sozolab.zampa.R
import com.sozolab.zampa.data.model.SubscriptionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val merchant by viewModel.merchant.collectAsState()
    val promoFreeUntilMs by viewModel.promoFreeUntilMs.collectAsState()
    val error by viewModel.error.collectAsState()

    val nowMs = System.currentTimeMillis()
    val promoActive = (promoFreeUntilMs ?: 0L) > nowMs
    val status = merchant?.subscriptionStatus ?: SubscriptionStatus.TRIAL
    val trialDays = merchant?.trialDaysRemaining()
    val canPublish = promoActive || (merchant?.canPublish() ?: true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.subscription_close))
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Estado actual ─────────────────────────────────────────────
            StatusBanner(status = status, trialDays = trialDays, canPublish = canPublish, promoFreeUntilMs = if (promoActive) promoFreeUntilMs else null)

            // ── Value props ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ValueRow(stringResource(R.string.subscription_value_unlimited))
                    ValueRow(stringResource(R.string.subscription_value_push))
                    ValueRow(stringResource(R.string.subscription_value_stats))
                    ValueRow(stringResource(R.string.subscription_value_profile))
                }
            }

            // ── CTA suscripción ───────────────────────────────────────────
            Text(
                stringResource(R.string.subscription_price_monthly),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = { /* TODO: integrar RevenueCat + Play Billing */ },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = false // Billing aún no integrado
            ) {
                Text(stringResource(R.string.subscription_subscribe_cta), fontWeight = FontWeight.Bold)
            }

            Text(
                stringResource(R.string.subscription_coming_soon_android),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.common_ok)) } }
        )
    }
}

/** Banner de estado: promo global, trial con countdown, activa, o expirada. */
@Composable
private fun StatusBanner(
    status: String,
    trialDays: Int?,
    canPublish: Boolean,
    promoFreeUntilMs: Long?,
) {
    val container: Color
    val onContainer: Color
    val title: String
    val body: String?

    when {
        promoFreeUntilMs != null -> {
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            title = stringResource(R.string.subscription_promo_title)
            val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, java.util.Locale.getDefault())
            body = stringResource(R.string.subscription_promo_body, fmt.format(java.util.Date(promoFreeUntilMs)))
        }
        !canPublish -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            title = stringResource(R.string.subscription_expired_title)
            body = stringResource(R.string.subscription_expired_body)
        }
        status == SubscriptionStatus.ACTIVE -> {
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            title = stringResource(R.string.subscription_active_title)
            body = null
        }
        else -> {
            // trial
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            title = when {
                trialDays == null -> stringResource(R.string.subscription_title)
                trialDays <= 0 -> stringResource(R.string.subscription_trial_ends_today)
                else -> stringResource(R.string.subscription_trial_days_remaining, trialDays)
            }
            body = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Star, null, tint = onContainer, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onContainer, textAlign = TextAlign.Center)
            body?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = onContainer, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ValueRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
