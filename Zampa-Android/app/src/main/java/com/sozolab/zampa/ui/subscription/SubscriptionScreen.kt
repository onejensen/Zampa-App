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
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val productDetails by viewModel.productDetails.collectAsState()
    val isPurchasing by viewModel.isPurchasing.collectAsState()
    val purchaseSuccessful by viewModel.purchaseSuccessful.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()
    val selectedOffer by viewModel.selectedOffer.collectAsState()
    val hasBothPlans by viewModel.hasBothPlans.collectAsState()
    val context = LocalContext.current

    val nowMs = System.currentTimeMillis()
    val promoActive = (promoFreeUntilMs ?: 0L) > nowMs
    val status = merchant?.subscriptionStatus ?: SubscriptionStatus.TRIAL
    val trialDays = merchant?.trialDaysRemaining()
    val canPublish = promoActive || (merchant?.canPublish() ?: true)
    val canPurchase = selectedOffer != null
        && !isPurchasing
        && !promoActive
        && status != SubscriptionStatus.ACTIVE

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

            // ── Toggle Mensual / Anual (solo si ambos base plans están cargados) ──
            // Ambos pills con la misma estructura (un Text) → mismo tamaño visual.
            // El "2 meses gratis" se muestra como badge separado bajo el row cuando
            // Anual esté seleccionado, en lugar de incrustarlo en la pill.
            if (hasBothPlans) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedPlan == SubscriptionViewModel.Plan.MONTHLY,
                        onClick = { viewModel.selectPlan(SubscriptionViewModel.Plan.MONTHLY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        )
                    ) {
                        Text(stringResource(R.string.subscription_plan_monthly))
                    }
                    SegmentedButton(
                        selected = selectedPlan == SubscriptionViewModel.Plan.ANNUAL,
                        onClick = { viewModel.selectPlan(SubscriptionViewModel.Plan.ANNUAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        )
                    ) {
                        Text(stringResource(R.string.subscription_plan_annual))
                    }
                }
                // Badge "2 meses gratis" sólo cuando Anual esté seleccionado.
                if (selectedPlan == SubscriptionViewModel.Plan.ANNUAL) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            stringResource(R.string.subscription_savings_2months),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // ── CTA suscripción ───────────────────────────────────────────
            // Precio del plan seleccionado, formateado por Google con la moneda
            // y duración correctas (ej: "24,99 €/mes" o "249,90 €/año").
            val pricingPhase = selectedOffer
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
            val priceLabel = pricingPhase?.formattedPrice?.let { price ->
                when (selectedPlan) {
                    SubscriptionViewModel.Plan.MONTHLY ->
                        stringResource(R.string.subscription_price_format, price)
                    SubscriptionViewModel.Plan.ANNUAL ->
                        stringResource(R.string.subscription_price_format_annual, price)
                }
            } ?: stringResource(R.string.subscription_price_monthly)
            Text(
                priceLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Sub-label con equivalencia mensual del plan anual ("20,82 €/mes facturado anualmente").
            if (selectedPlan == SubscriptionViewModel.Plan.ANNUAL && pricingPhase != null) {
                val monthlyEquiv = run {
                    val perMonthMicros = pricingPhase.priceAmountMicros / 12
                    val cents = perMonthMicros / 10000
                    val main = cents / 100
                    val frac = cents % 100
                    // Extraemos símbolo de moneda del formattedPrice ("24,99 €" → "€").
                    val currencyChar = pricingPhase.formattedPrice.replace(Regex("[\\d,.\\s]"), "")
                    "%d,%02d %s".format(main, frac, currencyChar).trim()
                }
                Text(
                    stringResource(R.string.subscription_annual_equivalent, monthlyEquiv),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    (context as? Activity)?.let { viewModel.launchPurchase(it) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = canPurchase
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPurchasing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(R.string.subscription_subscribe_cta_now),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.subscription_no_commitment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    if (purchaseSuccessful) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPurchaseSuccess(); onDismiss() },
            title = { Text(stringResource(R.string.subscription_active_title)) },
            text = { Text(stringResource(R.string.subscription_active_title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPurchaseSuccess(); onDismiss() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
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
