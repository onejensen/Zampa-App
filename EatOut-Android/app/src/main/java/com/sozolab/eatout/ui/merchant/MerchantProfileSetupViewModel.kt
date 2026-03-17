package com.sozolab.eatout.ui.merchant

import android.location.Geocoder
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.eatout.data.FirebaseService
import com.sozolab.eatout.data.model.Merchant
import com.sozolab.eatout.data.model.MerchantAddress
import com.sozolab.eatout.data.model.ScheduleEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditableSchedule(
    val day: String,
    val dayName: String,
    var isOpen: Boolean,
    var openTime: String,
    var closeTime: String
)

@HiltViewModel
class MerchantProfileSetupViewModel @Inject constructor(
    private val firebaseService: FirebaseService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSuccess = MutableStateFlow(false)
    val isSuccess: StateFlow<Boolean> = _isSuccess

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val schedule = MutableStateFlow(defaultSchedule())

    private fun defaultSchedule() = listOf(
        EditableSchedule("monday", "Lunes", true, "10:00", "23:00"),
        EditableSchedule("tuesday", "Martes", true, "10:00", "23:00"),
        EditableSchedule("wednesday", "Miércoles", true, "10:00", "23:00"),
        EditableSchedule("thursday", "Jueves", true, "10:00", "23:00"),
        EditableSchedule("friday", "Viernes", true, "10:00", "23:00"),
        EditableSchedule("saturday", "Sábado", true, "10:00", "23:00"),
        EditableSchedule("sunday", "Domingo", false, "10:00", "23:00")
    )

    fun saveProfile(
        name: String,
        phone: String,
        description: String,
        addressText: String,
        cuisineTypes: List<String>,
        acceptsReservations: Boolean,
        imageData: ByteArray?
    ) {
        val uid = firebaseService.currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                var coverUrl: String? = null
                if (imageData != null) {
                    coverUrl = firebaseService.uploadImage(imageData, "businesses/$uid/cover.jpg")
                }

                val finalSchedule = schedule.value.filter { it.isOpen }.map {
                    ScheduleEntry(it.day, it.openTime, it.closeTime)
                }

                val geocodedAddress = geocodeAddress(addressText)

                val merchant = Merchant(
                    id = uid,
                    name = name,
                    phone = phone,
                    shortDescription = description,
                    address = geocodedAddress,
                    cuisineTypes = cuisineTypes,
                    acceptsReservations = acceptsReservations,
                    coverPhotoUrl = coverUrl
                )

                firebaseService.updateMerchantProfile(merchant)
                _isSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Error al guardar el perfil"
            }
            _isLoading.value = false
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeAddress(addressText: String): MerchantAddress =
        withContext(Dispatchers.IO) {
            try {
                val results = Geocoder(context).getFromLocationName(addressText, 1)
                val loc = results?.firstOrNull()
                if (loc != null) {
                    MerchantAddress(addressText, loc.latitude, loc.longitude)
                } else {
                    MerchantAddress(addressText, 0.0, 0.0)
                }
            } catch (e: Exception) {
                MerchantAddress(addressText, 0.0, 0.0)
            }
        }
}
