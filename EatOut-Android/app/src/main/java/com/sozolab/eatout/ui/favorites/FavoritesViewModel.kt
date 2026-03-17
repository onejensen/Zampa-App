package com.sozolab.eatout.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.eatout.data.FirebaseService
import com.sozolab.eatout.data.model.Merchant
import com.sozolab.eatout.data.model.Menu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.value = FavoritesUiState.Loading
            try {
                val favs = firebaseService.getFavorites()
                // Cargar perfiles y menús en paralelo
                val merchantsWithMenus = coroutineScope {
                    favs.map { fav ->
                        async {
                            val merchant = firebaseService.getMerchantProfile(fav.businessId)
                                ?: return@async null
                            val menus = firebaseService.getMenusByMerchant(fav.businessId)
                            MerchantWithMenu(merchant, menus.firstOrNull { it.isActive })
                        }
                    }.awaitAll().filterNotNull()
                }
                _uiState.value = FavoritesUiState.Success(merchantsWithMenus)
            } catch (e: Exception) {
                _uiState.value = FavoritesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun removeFavorite(merchantId: String) {
        viewModelScope.launch {
            try {
                firebaseService.removeFavorite(merchantId)
                val currentState = _uiState.value
                if (currentState is FavoritesUiState.Success) {
                    val newList = currentState.merchants.filter { it.merchant.id != merchantId }
                    _uiState.value = FavoritesUiState.Success(newList)
                }
            } catch (e: Exception) {
                // Silently fail or post toast
            }
        }
    }
}

sealed class FavoritesUiState {
    object Loading : FavoritesUiState()
    data class Success(val merchants: List<MerchantWithMenu>) : FavoritesUiState()
    data class Error(val message: String) : FavoritesUiState()
}

data class MerchantWithMenu(
    val merchant: Merchant,
    val lastMenu: Menu?
)
