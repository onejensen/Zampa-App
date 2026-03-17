package com.sozolab.eatout.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.sozolab.eatout.data.FirebaseService
import com.sozolab.eatout.data.LocationService
import com.sozolab.eatout.data.model.Menu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val firebaseService: FirebaseService,
    private val locationService: LocationService
) : ViewModel() {

    private val _menus = MutableStateFlow<List<Menu>>(emptyList())
    val menus: StateFlow<List<Menu>> = _menus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _userCity = MutableStateFlow("Configurar ubicación")
    val userCity: StateFlow<String> = _userCity

    val userLocation: StateFlow<android.location.Location?> = locationService.currentLocation

    private var lastDoc: DocumentSnapshot? = null
    var canLoadMore = true
        private set

    private var selectedCuisine: String? = null
    private var maxPrice: Double? = null
    private var maxDistanceKm: Double? = null
    private var onlyFavorites: Boolean = false

    init {
        loadMenus()
        observeCity()
    }

    private fun observeCity() {
        viewModelScope.launch {
            locationService.city.collect { city ->
                city?.let { _userCity.value = it }
            }
        }
    }

    fun applyFilters(cuisine: String?, price: Double?, distanceKm: Double?, favoritesOnly: Boolean) {
        selectedCuisine = cuisine
        maxPrice = price
        maxDistanceKm = distanceKm
        onlyFavorites = favoritesOnly
        loadMenus()
    }

    private val hasClientSideFilters get() = maxDistanceKm != null || onlyFavorites

    fun loadMenus() {
        if (_isLoading.value) return
        _isLoading.value = true
        lastDoc = null
        canLoadMore = true

        viewModelScope.launch {
            try {
                // 1. Load favorite businessIds if needed
                val favIds: Set<String> = if (onlyFavorites) {
                    runCatching { firebaseService.getFavorites().map { it.businessId }.toSet() }
                        .getOrDefault(emptySet())
                } else emptySet()

                // 2. Fetch from Firestore (server-side: cuisine + price)
                val result = firebaseService.getActiveMenus(
                    limit = 20,
                    cuisineFilter = selectedCuisine,
                    maxPrice = maxPrice
                )
                var filtered = result.menus

                // 3. Client-side favorites filter
                if (onlyFavorites) {
                    filtered = filtered.filter { it.businessId in favIds }
                }

                // 4. Client-side distance filter
                val maxDist = maxDistanceKm
                if (maxDist != null) {
                    val userLoc = locationService.currentLocation.value
                    if (userLoc != null) {
                        val uniqueIds = filtered.map { it.businessId }.toSet()
                        val merchantMap = uniqueIds.map { id ->
                            async { id to runCatching { firebaseService.getMerchantProfile(id) }.getOrNull() }
                        }.awaitAll().toMap()

                        filtered = filtered.filter { menu ->
                            val addr = merchantMap[menu.businessId]?.address ?: return@filter false
                            val merchantLoc = android.location.Location("").apply {
                                latitude = addr.lat
                                longitude = addr.lng
                            }
                            userLoc.distanceTo(merchantLoc) / 1000.0 <= maxDist
                        }
                    }
                }

                _menus.value = filtered
                lastDoc = result.lastDoc
                // Disable pagination when client-side filters are active
                canLoadMore = result.menus.size == 20 && !hasClientSideFilters
            } catch (e: Exception) {
                // Log error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoading.value || !canLoadMore || hasClientSideFilters) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = firebaseService.getActiveMenus(
                    limit = 20,
                    lastDocument = lastDoc,
                    cuisineFilter = selectedCuisine,
                    maxPrice = maxPrice
                )
                _menus.value = _menus.value + result.menus
                lastDoc = result.lastDoc
                canLoadMore = result.menus.size == 20
            } catch (e: Exception) {
                // Log error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
