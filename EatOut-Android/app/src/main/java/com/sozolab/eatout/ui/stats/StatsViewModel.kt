package com.sozolab.eatout.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sozolab.eatout.data.FirebaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class DailyStat(
    val date: Date,
    val dateStr: String,
    val impressions: Int,
    val favorites: Int,
    val calls: Int,
    val directions: Int,
    val shares: Int
)

data class StatsState(
    val stats: List<DailyStat> = emptyList(),
    val isLoading: Boolean = true,
    val timeRangeDays: Int = 7
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsState())
    val uiState: StateFlow<StatsState> = _uiState

    init {
        loadStats()
    }

    fun setTimeRange(days: Int) {
        _uiState.value = _uiState.value.copy(timeRangeDays = days)
        loadStats()
    }

    fun loadStats() {
        val merchantId = firebaseService.currentUid ?: return
        val days = _uiState.value.timeRangeDays
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val rawStats = firebaseService.getMerchantStats(merchantId, days)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                
                val parsed = rawStats.mapNotNull { dict ->
                    val dateStr = dict["date"] as? String ?: return@mapNotNull null
                    val date = sdf.parse(dateStr) ?: return@mapNotNull null
                    val clicks = dict["clicks"] as? Map<*, *> ?: emptyMap<Any, Any>()
                    
                    DailyStat(
                        date = date,
                        dateStr = dateStr,
                        impressions = (dict["impressions"] as? Number)?.toInt() ?: 0,
                        favorites = (dict["favorites"] as? Number)?.toInt() ?: 0,
                        calls = (clicks["call"] as? Number)?.toInt() ?: 0,
                        directions = (clicks["directions"] as? Number)?.toInt() ?: 0,
                        shares = (clicks["share"] as? Number)?.toInt() ?: 0
                    )
                }.sortedBy { it.date }
                
                _uiState.value = _uiState.value.copy(stats = parsed, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
