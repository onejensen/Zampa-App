package com.sozolab.zampa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sozolab.zampa.data.ThemeManager

val LocalThemeManager = compositionLocalOf<ThemeManager?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun brandTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
)

@Composable
fun brandFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
fun brandFilterChipBorder(selected: Boolean): BorderStroke? = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
)

// Acento único: naranja brand para TODO. `secondary`/`tertiary` se mapean a
// Primary para evitar el verde teal por defecto en Switches/Chips.
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Surface,
    primaryContainer = PrimarySurface,
    onPrimaryContainer = PrimaryDark,
    secondary = Primary,
    onSecondary = Surface,
    secondaryContainer = PrimarySurface,
    onSecondaryContainer = PrimaryDark,
    tertiary = Primary,
    onTertiary = Surface,
    tertiaryContainer = PrimarySurface,
    onTertiaryContainer = PrimaryDark,
    background = Background,
    surface = Surface,
    surfaceContainer = InputBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = InputBackground,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary,
    outlineVariant = Divider,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = SurfaceDark,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimarySurface,
    secondary = Primary,
    onSecondary = SurfaceDark,
    secondaryContainer = PrimaryDark,
    onSecondaryContainer = PrimarySurface,
    tertiary = Primary,
    onTertiary = SurfaceDark,
    tertiaryContainer = PrimaryDark,
    onTertiaryContainer = PrimarySurface,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceContainer = InputBackgroundDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = InputBackgroundDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = TextSecondaryDark,
    outlineVariant = DividerDark,
)

@Composable
fun ZampaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ZampaShapes,
        content = content
    )
}
