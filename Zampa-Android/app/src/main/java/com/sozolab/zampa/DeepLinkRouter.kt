package com.sozolab.zampa

import android.content.Intent
import android.net.Uri

/**
 * Parses incoming intents (App Links + custom scheme `zampa://`) and extracts
 * the offer ID to open in `MenuDetailScreen`. Returns `null` if the intent
 * doesn't carry a supported deep link.
 *
 * Supported formats:
 * - `https://eatout-70b8b.web.app/o/{offerId}`
 * - `https://eatout-70b8b.firebaseapp.com/o/{offerId}`
 * - `zampa://offer/{offerId}`
 */
object DeepLinkRouter {

    private val supportedHosts = setOf(
        "eatout-70b8b.web.app",
        "eatout-70b8b.firebaseapp.com",
    )

    fun offerIdFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri: Uri = intent.data ?: return null
        return offerIdFrom(uri)
    }

    fun offerIdFrom(uri: Uri): String? {
        val scheme = uri.scheme ?: return null
        if (scheme == "zampa") {
            // zampa://offer/{id}
            if (uri.host != "offer") return null
            val id = uri.pathSegments.firstOrNull()
            return id?.takeIf { it.isNotBlank() }
        }
        if (scheme == "https") {
            val host = uri.host ?: return null
            if (host !in supportedHosts) return null
            val segments = uri.pathSegments
            if (segments.size < 2) return null
            if (segments[0] != "o") return null
            return segments[1].takeIf { it.isNotBlank() }
        }
        return null
    }
}
