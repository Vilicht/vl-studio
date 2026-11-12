package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VilichtDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = CharcoalMain,
    primaryContainer = CharcoalSurface,
    onPrimaryContainer = ChampagneGoldLight,
    secondary = TextSecondary,
    onSecondary = CharcoalMain,
    secondaryContainer = CharcoalPanel,
    onSecondaryContainer = TextPrimary,
    tertiary = ChampagneGoldLight,
    onTertiary = CharcoalMain,
    background = CharcoalMain,
    onBackground = TextPrimary,
    surface = CharcoalSecondary,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalPanel,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalBorder,
    outlineVariant = CharcoalSurface,
    error = StatusRed,
    onError = TextPrimary
)

@Composable
fun VilichtStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // VILICHT LIVE STUDIO is a dedicated pro studio that always adopts its refined charcoal identity
    MaterialTheme(
        colorScheme = VilichtDarkColorScheme,
        typography = Typography,
        content = content
    )
}
