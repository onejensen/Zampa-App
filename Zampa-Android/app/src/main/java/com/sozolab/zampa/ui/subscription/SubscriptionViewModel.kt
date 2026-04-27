package com.sozolab.zampa.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.Merchant
import com.sozolab.zampa.data.model.SubscriptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _merchant = MutableStateFlow<Merchant?>(null)
    val merchant: StateFlow<Merchant?> = _merchant

    private val _promoFreeUntilMs = MutableStateFlow<Long?>(null)
    val promoFreeUntilMs: StateFlow<Long?> = _promoFreeUntilMs

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadStatus() }

    private fun loadStatus() {
        val uid = firebaseService.currentUid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _merchant.value = firebaseService.getMerchantProfile(uid)
                _promoFreeUntilMs.value = firebaseService.getPromoFreeUntilMs()
            } catch (_: Exception) {
                // silent
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Pagos in-app pendientes de integrar (RevenueCat + Google Play Billing).
    // El botón de suscribirse dispara `onSubscribeClick` pero de momento no hace nada:
    // cuando se integre el SDK, aquí se invoca la compra y tras el webhook Firestore
    // actualizará `subscriptionStatus`/`subscriptionActiveUntil`.

    fun clearError() { _error.value = null }
}
