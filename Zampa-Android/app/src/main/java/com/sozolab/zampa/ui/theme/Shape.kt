package com.sozolab.zampa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Aligns Material3 shape tokens with iOS AppRadius (sm=8, md=12, lg=16, xl=20).
// `small` is bumped so FilterChip/AssistChip render as iOS-style pills.
val ZampaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
