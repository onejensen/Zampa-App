package com.sozolab.zampa.ui.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.DietaryInfo
import com.sozolab.zampa.data.model.Menu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class TodayStats(
    val impressions: Int = 0,
    val clicks: Int = 0,
    val favorites: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _menus = MutableStateFlow<List<Menu>>(emptyList())
    val menus: StateFlow<List<Menu>> = _menus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _createSuccess = MutableStateFlow(false)
    val createSuccess: StateFlow<Boolean> = _createSuccess

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _businessName = MutableStateFlow("")
    val businessName: StateFlow<String> = _businessName

    private val _todayStats = MutableStateFlow(TodayStats())
    val todayStats: StateFlow<TodayStats> = _todayStats

    init { loadMenus() }

    fun loadMenus() {
        val uid = firebaseService.currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val profile = firebaseService.getMerchantProfile(uid)
                _isPremium.value = profile?.planTier == "pro"
                _businessName.value = profile?.name ?: ""

                _menus.value = firebaseService.getMenusByMerchant(uid)
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
            loadTodayStats()
        }
    }

    private fun loadTodayStats() {
        val uid = firebaseService.currentUid ?: return
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        viewModelScope.launch {
            try {
                val doc = firebaseService.db
                    .collection("metrics").document(uid)
                    .collection("daily").document(dateStr)
                    .get().await()
                val data = doc.data ?: return@launch
                val clicks = data["clicks"] as? Map<*, *> ?: emptyMap<Any, Any>()
                _todayStats.value = TodayStats(
                    impressions = (data["impressions"] as? Number)?.toInt() ?: 0,
                    clicks = ((clicks["call"] as? Number)?.toInt() ?: 0) + ((clicks["directions"] as? Number)?.toInt() ?: 0),
                    favorites = (data["favorites"] as? Number)?.toInt() ?: 0
                )
            } catch (_: Exception) {}
        }
    }

    fun updateBusinessName(name: String) {
        viewModelScope.launch {
            try {
                val uid = firebaseService.currentUid ?: return@launch
                firebaseService.db.collection("businesses").document(uid)
                    .update("name", name)
                    .await()
                _businessName.value = name
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
        }
    }

    fun createMenu(title: String, description: String, price: Double, photoData: ByteArray, tags: List<String>, dietaryInfo: DietaryInfo = DietaryInfo(), offerType: String? = null, includesDrink: Boolean = false, includesDessert: Boolean = false, includesCoffee: Boolean = false, serviceTime: String = "both", isPermanent: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                firebaseService.createMenu(title, description, price, "EUR", photoData, tags, dietaryInfo, offerType, includesDrink, includesDessert, includesCoffee, serviceTime, isPermanent)
                _createSuccess.value = true
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }

    fun updateMenu(menuId: String, title: String, description: String, price: Double, tags: List<String>, photoData: ByteArray?, dietaryInfo: DietaryInfo = DietaryInfo(), offerType: String? = null, includesDrink: Boolean = false, includesDessert: Boolean = false, includesCoffee: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updateData = mutableMapOf<String, Any>(
                    "title" to title,
                    "description" to description,
                    "priceTotal" to price,
                    "tags" to tags,
                    "dietaryInfo" to dietaryInfo.toMap(),
                    "includesDrink" to includesDrink,
                    "includesDessert" to includesDessert,
                    "includesCoffee" to includesCoffee
                )
                offerType?.let { updateData["offerType"] = it }

                if (photoData != null) {
                    val imagePath = "dailyOffers/${java.util.UUID.randomUUID()}.jpg"
                    val photoUrl = firebaseService.uploadImage(photoData, imagePath)
                    updateData["photoUrls"] = listOf(photoUrl)
                }

                firebaseService.updateMenu(menuId, updateData)
                _createSuccess.value = true
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }

    fun deleteMenu(menuId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                firebaseService.deleteMenu(menuId)
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }

    fun deleteMenus(ids: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                ids.forEach { firebaseService.deleteMenu(it) }
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }

    fun resetCreateSuccess() { _createSuccess.value = false }
    fun clearError() { _error.value = null }
}
