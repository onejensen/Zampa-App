package com.sozolab.zampa.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sozolab.zampa.R

enum class TourTarget {
    FEED_CARD, FILTER_BUTTON, MAP_TOGGLE, FAVORITES_TAB,
    MERCHANT_DASHBOARD_TAB, MERCHANT_CREATE_BUTTON, MERCHANT_STATS_GRID
}

data class TourBounds(val offset: Offset, val size: Size)

data class TourStep(
    val target: TourTarget,
    val titleRes: Int,
    val descRes: Int
) {
    companion object {
        val clientSteps = listOf(
            TourStep(TourTarget.FEED_CARD,       R.string.tour_feed_title,                R.string.tour_feed_desc),
            TourStep(TourTarget.FILTER_BUTTON,   R.string.tour_filters_title,             R.string.tour_filters_desc),
            TourStep(TourTarget.MAP_TOGGLE,      R.string.tour_map_title,                 R.string.tour_map_desc),
            TourStep(TourTarget.FAVORITES_TAB,   R.string.tour_favorites_title,           R.string.tour_favorites_desc),
        )

        val merchantSteps = listOf(
            TourStep(TourTarget.MERCHANT_DASHBOARD_TAB,  R.string.tour_merchant_dashboard_title, R.string.tour_merchant_dashboard_desc),
            TourStep(TourTarget.MERCHANT_CREATE_BUTTON,  R.string.tour_merchant_create_title,    R.string.tour_merchant_create_desc),
            TourStep(TourTarget.MERCHANT_STATS_GRID,     R.string.tour_merchant_stats_title,     R.string.tour_merchant_stats_desc),
        )
    }
}
