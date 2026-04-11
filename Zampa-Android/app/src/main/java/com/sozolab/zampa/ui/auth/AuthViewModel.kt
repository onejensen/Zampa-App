package com.sozolab.zampa.ui.auth

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseService: FirebaseService,
    private val currencyService: com.sozolab.zampa.data.CurrencyService,
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(firebaseService.isAuthenticated)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Pending social user waiting for role selection
    private val _pendingSocialUser = MutableStateFlow<User?>(null)
    val pendingSocialUser: StateFlow<User?> = _pendingSocialUser

    // Cuenta pendiente de eliminación detectada tras login. Mientras sea no-nulo,
    // la navegación muestra AccountDeletionRecoveryScreen en lugar de Main.
    private val _pendingDeletionUser = MutableStateFlow<User?>(null)
    val pendingDeletionUser: StateFlow<User?> = _pendingDeletionUser

    // Preview local mientras se sube la foto; sobrevive cambios de pestaña
    private val _pendingPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingPhotoBitmap = _pendingPhotoBitmap.asStateFlow()

    init {
        if (firebaseService.isAuthenticated) {
            viewModelScope.launch {
                val uid = firebaseService.currentUid ?: return@launch
                val user = firebaseService.getUserProfile(uid)
                if (user != null) {
                    routePostLogin(user)
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val user = firebaseService.login(email, password)
                routePostLogin(user)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Error al iniciar sesión"
            }
            _isLoading.value = false
        }
    }

    fun register(email: String, password: String, name: String, role: User.UserRole, phone: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val user = firebaseService.register(email, password, name, role, phone)
                // Un registro recién creado nunca tiene deletedAt, pero pasamos por
                // routePostLogin para mantener un único punto de entrada.
                routePostLogin(user)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Error al registrarse"
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        firebaseService.logout()
        _isAuthenticated.value = false
        _currentUser.value = null
        _pendingDeletionUser.value = null
    }

    fun clearError() { _error.value = null }

    // ── Google Sign-In ──

    fun getGoogleSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.sozolab.zampa.R.string.default_web_client_id))
            .requestEmail()
            .build()
        // Sign out first so the account picker always appears
        GoogleSignIn.getClient(context, gso).signOut()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken ?: throw Exception("No se obtuvo el token de Google")
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val (user, isNewUser) = firebaseService.signInWithCredential(credential)
                if (isNewUser) {
                    _pendingSocialUser.value = user   // show role selection
                } else {
                    routePostLogin(user)
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501) {  // 12501 = user cancelled
                    _error.value = "Error al iniciar sesión con Google (${e.statusCode})"
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Error al iniciar sesión con Google"
            }
            _isLoading.value = false
        }
    }

    fun finalizeSocialRegistration(role: User.UserRole, name: String) {
        val pending = _pendingSocialUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = firebaseService.finalizeSocialRegistration(pending.id, role, name, pending.email)
                _pendingSocialUser.value = null
                routePostLogin(user)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Error al completar el registro"
            }
            _isLoading.value = false
        }
    }

    fun cancelSocialRegistration() {
        _pendingSocialUser.value = null
        firebaseService.logout()
    }

    private fun refreshDeviceToken() {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                firebaseService.registerDeviceToken(token)
            } catch (_: Exception) {}
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            try {
                firebaseService.updateUserName(name)
                val uid = firebaseService.currentUid ?: return@launch
                _currentUser.value = firebaseService.getUserProfile(uid)
            } catch (_: Exception) {}
        }
    }

    fun updateProfilePhoto(bitmap: Bitmap, photoData: ByteArray) {
        _pendingPhotoBitmap.value = bitmap
        viewModelScope.launch {
            try {
                val url = firebaseService.uploadProfilePhoto(photoData)
                _currentUser.value = _currentUser.value?.copy(photoUrl = url)
                _pendingPhotoBitmap.value = null  // Solo limpiar tras éxito
            } catch (_: Exception) {
                // En error mantenemos pendingPhotoBitmap para no revertir la UI
            }
        }
    }

    /**
     * Ruta el resultado de login: si el usuario tiene deletedAt activo, no
     * marca la sesión como autenticada (para que NavHost no vaya a Main) y
     * expone el usuario via pendingDeletionUser para que el host muestre la
     * pantalla de recuperación.
     */
    private fun routePostLogin(user: User) {
        if (user.deletedAt != null) {
            _pendingDeletionUser.value = user
            _currentUser.value = user
            _isAuthenticated.value = false
        } else {
            _pendingDeletionUser.value = null
            _currentUser.value = user
            _isAuthenticated.value = true
            refreshDeviceToken()
            viewModelScope.launch { currencyService.loadIfNeeded() }
        }
    }

    // ── Account deletion ──

    /**
     * Solicita eliminación de cuenta. Tras el éxito, reemplaza el usuario
     * cargado por uno nuevo con deletedAt seteado, lo cual hace que
     * NavHost salte a AccountDeletionRecoveryScreen.
     */
    fun requestAccountDeletion(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.requestAccountDeletion()
                // Per spec §3.2: cerrar sesión tras el batch y volver al auth entry.
                // Cuando el usuario inicie sesión de nuevo, routePostLogin detectará
                // deletedAt y expondrá pendingDeletionUser.
                firebaseService.logout()
                _isAuthenticated.value = false
                _currentUser.value = null
                _pendingDeletionUser.value = null
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar cuenta")
            }
        }
    }

    /** Cancela una eliminación pendiente y reactiva la cuenta. */
    fun cancelAccountDeletion(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.cancelAccountDeletion()
                val uid = firebaseService.currentUid ?: return@launch
                val refreshed = firebaseService.getUserProfile(uid) ?: return@launch
                routePostLogin(refreshed)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al recuperar cuenta")
            }
        }
    }

    /**
     * Solicita un cambio de moneda preferida. Tras el éxito, refresca el
     * User cargado para propagar el cambio a todas las pantallas que
     * observan currentUser.
     */
    fun updateCurrencyPreference(code: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.updateCurrencyPreference(code)
                val uid = firebaseService.currentUid ?: return@launch
                val refreshed = firebaseService.getUserProfile(uid) ?: return@launch
                _currentUser.value = refreshed
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al cambiar la moneda")
            }
        }
    }

    /**
     * Devuelve el string formateado del importe convertido a la moneda destino,
     * usando el Hilt singleton que ya tiene las tasas cargadas (o fallback).
     * Null si la conversión no es posible.
     */
    fun formatConverted(eurAmount: Double, to: String): String? {
        return currencyService.formatConverted(eurAmount, to)
    }
}
