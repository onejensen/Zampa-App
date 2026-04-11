package com.sozolab.zampa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sozolab.zampa.ui.navigation.ZampaNavHost
import com.sozolab.zampa.ui.theme.ZampaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
            ZampaTheme {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra("menuId") ?: DeepLinkRouter.offerIdFrom(intent)
        if (id != null) deepLinkOfferId.value = id
    }
}
