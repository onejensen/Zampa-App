package com.sozolab.zampa

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sozolab.zampa.data.ThemeManager
import com.sozolab.zampa.ui.navigation.ZampaNavHost
import com.sozolab.zampa.ui.theme.LocalThemeManager
import com.sozolab.zampa.ui.theme.ZampaTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager

    // Hot stream para los deep links: emite el offer ID inicial y cualquier
    // intent posterior (onNewIntent) cuando la app ya está en primer plano.
    private val deepLinkOfferId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compatibilidad con el extra "menuId" anterior (notificaciones push) +
        // nuevos deep links via Intent.ACTION_VIEW.
        val initialId = intent.getStringExtra("menuId")
            ?: DeepLinkRouter.offerIdFrom(intent)
        deepLinkOfferId.value = initialId

        enableEdgeToEdge()
        setContent {
            val themePref by themeManager.theme.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themePref) {
                ThemeManager.LIGHT -> false
                ThemeManager.DARK -> true
                else -> systemDark
            }
            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                ZampaTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val pendingId by deepLinkOfferId.collectAsState()
                        ZampaNavHost(
                            startMenuId = pendingId,
                            onMenuConsumed = { deepLinkOfferId.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra("menuId") ?: DeepLinkRouter.offerIdFrom(intent)
        if (id != null) deepLinkOfferId.value = id
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("zampa_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("appLanguage", "auto") ?: "auto"
        val supported = setOf("es", "ca", "eu", "gl", "en", "de", "fr", "it")
        val resolved = if (saved == "auto") {
            val sys = Locale.getDefault().language
            if (sys in supported) sys else "es"
        } else saved
        val locale = Locale(resolved)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
