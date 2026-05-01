package com.sozolab.zampa.ui.subscription

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.Merchant
import com.sozolab.zampa.data.model.SubscriptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseService: FirebaseService,
) : ViewModel() {

    companion object {
        const val PRODUCT_ID = "zampa_pro_monthly"
        // Base plan IDs definidos en Play Console.
        const val BASE_PLAN_MONTHLY = "monthly"
        const val BASE_PLAN_ANNUAL = "annual"
    }

    enum class Plan { MONTHLY, ANNUAL }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _merchant = MutableStateFlow<Merchant?>(null)
    val merchant: StateFlow<Merchant?> = _merchant

    private val _promoFreeUntilMs = MutableStateFlow<Long?>(null)
    val promoFreeUntilMs: StateFlow<Long?> = _promoFreeUntilMs

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing

    private val _purchaseSuccessful = MutableStateFlow(false)
    val purchaseSuccessful: StateFlow<Boolean> = _purchaseSuccessful

    private val _selectedPlan = MutableStateFlow(Plan.ANNUAL)
    val selectedPlan: StateFlow<Plan> = _selectedPlan

    fun selectPlan(plan: Plan) { _selectedPlan.value = plan }

    /** Devuelve la base plan seleccionada (offer details) si está cargada. */
    val selectedOffer: StateFlow<ProductDetails.SubscriptionOfferDetails?> =
        kotlinx.coroutines.flow.combine(_productDetails, _selectedPlan) { product, plan ->
            val targetBasePlanId = when (plan) {
                Plan.MONTHLY -> BASE_PLAN_MONTHLY
                Plan.ANNUAL -> BASE_PLAN_ANNUAL
            }
            product?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == targetBasePlanId }
                ?: product?.subscriptionOfferDetails?.firstOrNull()
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    /** ¿Hay base plans para ambas duraciones? Decide si mostrar el toggle en UI. */
    val hasBothPlans: StateFlow<Boolean> =
        _productDetails.map { product ->
            val ids = product?.subscriptionOfferDetails?.map { it.basePlanId } ?: emptyList()
            ids.contains(BASE_PLAN_MONTHLY) && ids.contains(BASE_PLAN_ANNUAL)
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    private var billingClient: BillingClient? = null

    init {
        loadStatus()
        connectBilling()
    }

    private fun loadStatus() {
        val uid = firebaseService.currentUid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _merchant.value = firebaseService.getMerchantProfile(uid)
                _promoFreeUntilMs.value = firebaseService.getPromoFreeUntilMs()
            } catch (_: Exception) { /* silent */ } finally {
                _isLoading.value = false
            }
        }
    }

    /** Conecta con Play Billing y consulta el producto. Sin actividad — sólo lectura. */
    private fun connectBilling() {
        val listener = PurchasesUpdatedListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
                BillingClient.BillingResponseCode.USER_CANCELED -> _isPurchasing.value = false
                else -> {
                    _error.value = "Error de Play Billing: ${result.debugMessage}"
                    _isPurchasing.value = false
                }
            }
        }

        val client = BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct(client)
                } else {
                    _error.value = "Play Billing no disponible (${result.debugMessage})."
                }
            }
            override fun onBillingServiceDisconnected() { /* el siguiente queryProductDetails reintentará */ }
        })
        billingClient = client
    }

    private fun queryProduct(client: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()

        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = list.firstOrNull()
                if (list.isEmpty()) {
                    _error.value = "Producto $PRODUCT_ID no configurado en Play Console."
                }
            } else {
                _error.value = "queryProductDetails falló: ${result.debugMessage}"
            }
        }
    }

    /**
     * Lanza el sheet de compra. Necesita `Activity` (no se puede lanzar desde ApplicationContext).
     * Antes de lanzar, garantiza que `businesses/{uid}.appAccountToken` existe — sin eso,
     * el webhook server-side no podría mapear la compra al merchant correcto.
     */
    fun launchPurchase(activity: Activity) {
        val client = billingClient ?: run {
            _error.value = "Play Billing no inicializado."
            return
        }
        val product = _productDetails.value ?: run {
            _error.value = "Producto no cargado."
            return
        }
        _isPurchasing.value = true
        viewModelScope.launch {
            try {
                val token = firebaseService.getOrCreateAppAccountToken()
                // Pickea el offer del base plan seleccionado (monthly o annual).
                // Si no se encuentra el seleccionado, fallback al primero (compat).
                val targetBasePlanId = when (_selectedPlan.value) {
                    Plan.MONTHLY -> BASE_PLAN_MONTHLY
                    Plan.ANNUAL -> BASE_PLAN_ANNUAL
                }
                val offer = product.subscriptionOfferDetails
                    ?.firstOrNull { it.basePlanId == targetBasePlanId }
                    ?: product.subscriptionOfferDetails?.firstOrNull()
                val offerToken = offer?.offerToken
                if (offerToken == null) {
                    _error.value = "El producto no tiene base plan activo en Play Console."
                    _isPurchasing.value = false
                    return@launch
                }

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .setOfferToken(offerToken)
                            .build()
                    ))
                    .setObfuscatedAccountId(token)
                    .build()

                client.launchBillingFlow(activity, flowParams)
                // El resultado llega vía PurchasesUpdatedListener (handlePurchase).
            } catch (e: Exception) {
                _error.value = "Error iniciando compra: ${e.message}"
                _isPurchasing.value = false
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Acknowledge: si no se hace en 3 días, Google reembolsa automáticamente.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _error.value = "Acknowledge falló: ${result.debugMessage}"
                }
            }
        }
        // Registrar purchaseToken → merchantId en backend. Es fallback por si
        // playRTDN no puede llamar a androidpublisher API (cuenta sin permisos
        // en Play Console). Idempotente: el doc se sobreescribe con cada compra.
        viewModelScope.launch {
            try {
                firebaseService.recordPlayPurchase(purchase.purchaseToken)
            } catch (_: Exception) { /* silent — el webhook tiene fallback adicional */ }
        }
        _isPurchasing.value = false
        _purchaseSuccessful.value = true
        // Refrescar el merchant tras unos segundos para recoger lo que escriba el webhook.
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            firebaseService.currentUid?.let { uid ->
                _merchant.value = firebaseService.getMerchantProfile(uid)
            }
        }
    }

    fun clearError() { _error.value = null }
    fun clearPurchaseSuccess() { _purchaseSuccessful.value = false }

    override fun onCleared() {
        billingClient?.endConnection()
        billingClient = null
        super.onCleared()
    }
}
