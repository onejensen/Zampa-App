package com.sozolab.eatout.ui.feed

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.eatout.data.FirebaseService
import com.sozolab.eatout.data.LocationService
import com.sozolab.eatout.data.model.Merchant
import com.sozolab.eatout.data.model.Menu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MenuDetailViewModel @Inject constructor(
    private val firebaseService: FirebaseService,
    private val locationService: LocationService
) : ViewModel() {

    private val _menu = MutableStateFlow<Menu?>(null)
    val menu: StateFlow<Menu?> = _menu

    private val _merchant = MutableStateFlow<Merchant?>(null)
    val merchant: StateFlow<Merchant?> = _merchant

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val userLocation: StateFlow<Location?> = locationService.currentLocation

    fun load(menuId: String) {
        viewModelScope.launch {
            try {
                val m = firebaseService.getMenuById(menuId)
                _menu.value = m
                m?.businessId?.let { mid ->
                    _merchant.value = firebaseService.getMerchantProfile(mid)
                    _isFavorite.value = firebaseService.isFavorite(mid)
                    firebaseService.trackImpression(mid)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            _menu.value?.businessId?.let { mid ->
                val newState = firebaseService.toggleFavorite(mid)
                _isFavorite.value = newState
            }
        }
    }

    fun trackAction(action: String) {
        _menu.value?.let { m ->
            viewModelScope.launch { firebaseService.trackAction(m.id, m.businessId, action) }
        }
    }
}
