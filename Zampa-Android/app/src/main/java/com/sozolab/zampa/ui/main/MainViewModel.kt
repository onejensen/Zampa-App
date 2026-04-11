package com.sozolab.zampa.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _needsSetup = MutableStateFlow(false)
    val needsSetup: StateFlow<Boolean> = _needsSetup

    private val _needsLocationPrompt = MutableStateFlow(false)
    val needsLocationPrompt: StateFlow<Boolean> = _needsLocationPrompt

    init {
        checkProfileStatus()
    }

    fun checkLocationPermission(context: android.content.Context) {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _needsLocationPrompt.value = true
        }
    }

    private fun checkProfileStatus() {
        val uid = firebaseService.currentUid ?: return
        viewModelScope.launch {
            try {
                val user = firebaseService.getUserProfile(uid)
                if (user?.role == User.UserRole.COMERCIO) {
                    val complete = firebaseService.isMerchantProfileComplete(uid)
                    _needsSetup.value = !complete
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun dismissSetup() {
        _needsSetup.value = false
    }

    fun dismissLocation() {
        _needsLocationPrompt.value = false
    }
}
