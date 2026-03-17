package com.sozolab.eatout.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.eatout.data.FirebaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    fun upgradeToPro() {
        val uid = firebaseService.currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Simulación de pasarela de pago
                kotlinx.coroutines.delay(2000)
                
                // Actualizar en Firestore
                firebaseService.db.collection("businesses").document(uid)
                    .update("planTier", "pro")
                    .await()
                    
                // Crear suscripcion
                firebaseService.createSubscription(businessId = uid, type = "MONTHLY")
                
                _isPremium.value = true
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }

    fun clearError() { _error.value = null }
}
