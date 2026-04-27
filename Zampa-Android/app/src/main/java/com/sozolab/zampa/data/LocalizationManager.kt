package com.sozolab.zampa.data

import android.content.Context
import android.content.res.Configuration
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SupportedLanguage(val code: String, val nativeName: String)

@Singleton
class LocalizationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        val supportedLanguages = listOf(
            SupportedLanguage("auto", "Automático"),
            SupportedLanguage("es", "Español"),
            SupportedLanguage("ca", "Català"),
            SupportedLanguage("eu", "Euskara"),
            SupportedLanguage("gl", "Galego"),
            SupportedLanguage("en", "English"),
            SupportedLanguage("de", "Deutsch"),
            SupportedLanguage("fr", "Français"),
            SupportedLanguage("it", "Italiano"),
        )
        private val supportedCodes = supportedLanguages.map { it.code }.filter { it != "auto" }
    }

    private val prefs = context.getSharedPreferences("zampa_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(prefs.getString("appLanguage", "auto") ?: "auto")
    val currentLanguage: StateFlow<String> = _currentLanguage

    val resolvedLanguage: String
        get() {
            val lang = _currentLanguage.value
            if (lang == "auto") {
                val systemLang = Locale.getDefault().language
                return if (systemLang in supportedCodes) systemLang else "es"
            }
            return lang
        }

    val resolvedLanguageNativeName: String
        get() {
            val lang = _currentLanguage.value
            if (lang == "auto") {
                val resolved = resolvedLanguage
                val name = supportedLanguages.find { it.code == resolved }?.nativeName ?: "Español"
                return "Automático ($name)"
            }
            return supportedLanguages.find { it.code == lang }?.nativeName ?: lang
        }

    fun createLocalizedContext(): Context {
        val locale = Locale(resolvedLanguage)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun setLanguage(code: String, userId: String? = null) {
        _currentLanguage.value = code
        prefs.edit().putString("appLanguage", code).apply()
        userId?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("languagePreference", code)
        }
    }

    fun syncFromFirebase(userId: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val lang = doc.getString("languagePreference")
                if (!lang.isNullOrEmpty()) {
                    _currentLanguage.value = lang
                    prefs.edit().putString("appLanguage", lang).apply()
                }
            }
    }
}
