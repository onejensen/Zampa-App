package com.sozolab.zampa.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.R
import com.sozolab.zampa.ui.theme.Primary

@Composable
fun TourOverlay(
    state: TourState,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    if (!state.isActive) return
    val step = state.currentStep ?: return
    val bounds = state.currentBounds

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color(0xC7000000))
            if (bounds != null) {
                drawSpotlight(bounds)
            }
        }

        TourTooltip(
            step = step,
            stepIndex = state.currentStepIndex,
            totalSteps = state.steps.size,
            isLast = state.isLastStep,
            bounds = bounds,
            onNext = onNext,
            onSkip = onSkip
        )
    }
}

private fun DrawScope.drawSpotlight(bounds: TourBounds) {
    drawRoundRect(
        color = Color.Transparent,
        blendMode = BlendMode.Clear,
        topLeft = Offset(bounds.offset.x - 6f, bounds.offset.y - 6f),
        size = Size(bounds.size.width + 12f, bounds.size.height + 12f),
        cornerRadius = CornerRadius(12.dp.toPx())
    )
}

@Composable
private fun TourTooltip(
    step: TourStep,
    stepIndex: Int,
    totalSteps: Int,
    isLast: Boolean,
    bounds: TourBounds?,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val tooltipWidth = 260.dp

    val (tooltipOffsetX, tooltipOffsetY) = if (bounds != null) {
        val targetMidXDp: Dp = with(density) { (bounds.offset.x + bounds.size.width / 2).toDp() }
        val showAbove = bounds.offset.y + bounds.size.height > screenHeightPx * 0.55f
        val targetBottomDp: Dp = with(density) { (bounds.offset.y + bounds.size.height).toDp() }
        val targetTopDp: Dp = with(density) { bounds.offset.y.toDp() }

        val offsetX = (targetMidXDp - tooltipWidth / 2).coerceIn(8.dp, screenWidthDp - tooltipWidth - 8.dp)
        val offsetY = if (showAbove) targetTopDp - 160.dp else targetBottomDp + 8.dp
        Pair(offsetX, offsetY)
    } else {
        // Fallback: center on screen
        val centerX = screenWidthDp / 2 - tooltipWidth / 2
        val centerY = with(density) { (screenHeightPx * 0.4f).toDp() }
        Pair(centerX, centerY)
    }

    Box(
        modifier = Modifier
            .offset(x = tooltipOffsetX, y = tooltipOffsetY)
            .width(tooltipWidth)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(step.titleRes),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        "${stepIndex + 1} / $totalSteps",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    stringResource(step.descRes),
                    fontSize = 12.sp,
                    color = Color(0xFF555555),
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            stringResource(R.string.tour_skip),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isLast) stringResource(R.string.tour_finish)
                                   else stringResource(R.string.tour_next),
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
