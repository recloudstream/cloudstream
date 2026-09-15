package com.lagradost.cloudstream3.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Cinematic dark high-contrast theme for Compose TV.
 * Uses only [androidx.tv.material3] — never phone Material3.
 */
private val TvCinematicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF5C3B00),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF004D47),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF2A0030),
    background = Color(0xFF07070A),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF24242E),
    onSurfaceVariant = Color(0xFFC8C8D2),
    border = Color(0xFF5A5A68),
    borderVariant = Color(0xFF3A3A44),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0000),
    scrim = Color(0xCC000000),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvCinematicDarkColorScheme,
        content = content,
    )
}
