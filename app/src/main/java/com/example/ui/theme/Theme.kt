package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnshTraXColorScheme = darkColorScheme(
    primary = HarvestGold,
    onPrimary = DieselCharcoal,
    primaryContainer = TractorCrimson,
    onPrimaryContainer = CreamInk,
    secondary = CropEmerald,
    onSecondary = DieselCharcoal,
    secondaryContainer = CanopyCard,
    onSecondaryContainer = CreamInk,
    tertiary = SonalikaBlue,
    onTertiary = CreamInk,
    background = DieselCharcoal,
    onBackground = CreamInk,
    surface = AsphaltSurface,
    onSurface = CreamInk,
    surfaceVariant = CanopyCard,
    onSurfaceVariant = MutedWheat,
    outline = SteelBorder,
    error = DangerRed,
    onError = CreamInk
)

@Composable
fun AnshTraXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AnshTraXColorScheme,
        typography = Typography,
        content = content
    )
}
