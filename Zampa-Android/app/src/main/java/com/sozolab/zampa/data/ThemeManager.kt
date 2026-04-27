package com.sozolab.zampa.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(context: Context) {
    companion object {
        const val SYSTEM = "system"
        const val LIGHT = "light"
        const val DARK = "dark"
        val all = listOf(SYSTEM, LIGHT, DARK)
    }

    private val prefs = context.getSharedPreferences("zampa_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString("appTheme", SYSTEM) ?: SYSTEM)
    val theme: StateFlow<String> = _theme

    fun setTheme(value: String) {
        if (value !in all) return
        _theme.value = value
        prefs.edit().putString("appTheme", value).apply()
    }
}
