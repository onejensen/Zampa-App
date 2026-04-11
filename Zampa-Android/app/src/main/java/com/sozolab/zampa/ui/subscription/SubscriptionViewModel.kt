package com.sozolab.zampa.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.zampa.data.FirebaseService
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

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadStatus()
    }

    private fun loadStatus() {
        val uid = firebaseService.currentUid ?: return
        viewModelScope.launch {
            try {
                val profile = firebaseService.getMerchantProfile(uid)
                _isPremium.value = profile?.planTier == "pro"
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Pagos in-app pendientes de integrar (RevenueCat + Google Play Billing).
    // Se eliminó la simulación previa que escribía planTier="pro" sin pagar:
    // Firestore rules ahora rechazan cualquier escritura cliente a planTier.

    fun clearError() { _error.value = null }
}
