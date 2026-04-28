package com.sozolab.zampa.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.R
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Day-of-week picker for permanent offers.
 * weekday convention: 0=Monday … 6=Sunday (European Mon-first order).
 *
 * @param occupiedDays Days already claimed by other permanents of this merchant.
 * @param selectedDays The current selection for this offer.
 * @param onSelectionChange Called when the user toggles a day.
 */
@Composable
fun RecurringDaysPicker(
    occupiedDays: Set<Int>,
    selectedDays: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit
) {
    // Locale-aware short weekday names ordered Mon…Sun
    val rawSymbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    // rawSymbols[0] is empty, [1]=Sun, [2]=Mon, … [7]=Sat
    val orderedSymbols = listOf(
        rawSymbols[2], // Mon
        rawSymbols[3], // Tue
        rawSymbols[4], // Wed
        rawSymbols[5], // Thu
        rawSymbols[6], // Fri
        rawSymbols[7], // Sat
        rawSymbols[1]  // Sun
    )

    val freeSlotsCount = 7 - occupiedDays.size
    val allOccupied = occupiedDays.size >= 7
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = primary.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.create_menu_recurring_days_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = stringResource(R.string.create_menu_recurring_days_slots_free, freeSlotsCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Day buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (dayIndex in 0..6) {
                    val label = orderedSymbols[dayIndex].take(2)
                    val isOccupied = dayIndex in occupiedDays
                    val isSelected = dayIndex in selectedDays

                    val bgColor = when {
                        isSelected -> primary
                        isOccupied -> surface.copy(alpha = 0.5f)
                        else -> surface
                    }
                    val textColor = when {
                        isSelected -> Color.White
                        isOccupied -> onSurface.copy(alpha = 0.3f)
                        else -> onSurface
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(bgColor)
                            .then(
                                if (!isOccupied && !isSelected)
                                    Modifier.border(1.5.dp, onSurface.copy(alpha = 0.2f), CircleShape)
                                else Modifier
                            )
                            .clickable(enabled = !isOccupied) {
                                val newSet = selectedDays.toMutableSet()
                                if (isSelected) newSet.remove(dayIndex) else newSet.add(dayIndex)
                                onSelectionChange(newSet)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // All-occupied warning
            if (allOccupied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_menu_recurring_days_all_occupied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
