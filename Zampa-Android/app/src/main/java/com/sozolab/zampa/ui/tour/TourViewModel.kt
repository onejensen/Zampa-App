package com.sozolab.zampa.ui.tour

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TourState(
    val isActive: Boolean = false,
    val currentStepIndex: Int = 0,
    val steps: List<TourStep> = emptyList(),
    val bounds: Map<TourTarget, TourBounds> = emptyMap()
) {
    val currentStep: TourStep? get() = steps.getOrNull(currentStepIndex)
    val isLastStep: Boolean get() = currentStepIndex == steps.size - 1
    val currentBounds: TourBounds? get() = currentStep?.let { bounds[it.target] }
}

@HiltViewModel
class TourViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TourState())
    val state: StateFlow<TourState> = _state.asStateFlow()

    fun start(isMerchant: Boolean) {
        val steps = if (isMerchant) TourStep.merchantSteps else TourStep.clientSteps
        _state.update { it.copy(isActive = true, currentStepIndex = 0, steps = steps) }
    }

    fun next() {
        val s = _state.value
        if (!s.isActive) return
        if (s.currentStepIndex < s.steps.size - 1) {
            _state.update { it.copy(currentStepIndex = it.currentStepIndex + 1) }
        } else {
            _state.update { it.copy(isActive = false) }
        }
    }

    fun skip() {
        if (!_state.value.isActive) return
        _state.update { it.copy(isActive = false) }
    }

    fun registerBounds(target: TourTarget, bounds: TourBounds) {
        _state.update { it.copy(bounds = it.bounds + (target to bounds)) }
    }
}
