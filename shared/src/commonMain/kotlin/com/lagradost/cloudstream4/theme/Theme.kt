package com.lagradost.cloudstream4.theme

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class CloudStreamThemeMode {
    /** "Black" standard dark, #111111 backgrounds */
    Dark,

    /** "Amoled" / "AmoledLight" pure black (#000000) */
    Amoled,

    /** "AmoledLight" pure black (#000000) */
    AmoledLight,

    /** "Light" white/gray backgrounds, dark text */
    Light,

    /** "Dracula" */
    Dracula,

    /** "Lavender" */
    Lavender,

    /** "SilentBlue" */
    SilentBlue,

    /** "System" resolved on each platform via [isSystemInDarkTheme] */
    FollowSystem,

    /**
     * Uses platform dynamic color system, Material You on Android 12+,
     * falls back to [Dark] on unsupported platforms.
     */
    Dynamic,
}

@Composable
fun modeToTheme(mode : CloudStreamThemeMode, primaryColor: CloudStreamPrimaryColor) : CloudStreamColorScheme {
    val dynamicTheme = DeviceTheme.resolveDynamicTheme()
    val dynamicPrimary = DeviceTheme.resolveDynamicPrimaryColor()
    val dynamicSecondary = DeviceTheme.resolveDynamicSecondaryColor()
    val systemDark = isSystemInDarkTheme()
    val color = remember(mode, primaryColor, systemDark, dynamicTheme, dynamicPrimary, dynamicSecondary) {
        val base = when (mode) {
            CloudStreamThemeMode.Dark -> darkScheme()
            CloudStreamThemeMode.Amoled -> amoledScheme()
            CloudStreamThemeMode.AmoledLight -> amoledLightScheme()
            CloudStreamThemeMode.Light -> lightScheme()
            CloudStreamThemeMode.Dracula -> draculaScheme()
            CloudStreamThemeMode.Lavender -> lavenderScheme()
            CloudStreamThemeMode.SilentBlue -> silentBlueScheme()
            CloudStreamThemeMode.FollowSystem -> if (systemDark) darkScheme() else lightScheme()
            CloudStreamThemeMode.Dynamic -> dynamicTheme
        }
        when {
            mode == CloudStreamThemeMode.Dynamic -> base
            primaryColor == CloudStreamPrimaryColor.DYNAMIC -> base.copy(primary = dynamicPrimary)
            primaryColor == CloudStreamPrimaryColor.DYNAMIC_TWO -> base.copy(primary = dynamicSecondary)
            else -> base.copy(primary = primaryColor.color)
        }
    }
    return color
}

private fun CloudStreamColorScheme.toMaterial3ColorScheme() = if (isLight) {
    lightColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
} else {
    darkColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
}

val LocalSharedInfiniteTransition = staticCompositionLocalOf<InfiniteTransition> { throw NotImplementedError() }
val LocalCloudStreamColors = staticCompositionLocalOf { darkScheme() }

object CloudStreamTheme {
    val colors: CloudStreamColorScheme @Composable @ReadOnlyComposable get() = LocalCloudStreamColors.current
    /** Global synchronized animation, so many items in e.g. a LazyList can animate at the same time,
     * even if they appeared at different times */
    val transition: InfiniteTransition @Composable @ReadOnlyComposable get() = LocalSharedInfiniteTransition.current
}

@Composable
fun CloudStreamPreviewTheme(content: @Composable () -> Unit) {
    CloudStreamTheme(content = content)
}

@Composable
fun CloudStreamTheme(
    mode: CloudStreamThemeMode = CloudStreamThemeMode.FollowSystem,
    primaryColor: CloudStreamPrimaryColor = CloudStreamPrimaryColor.NORMAL,
    content: @Composable () -> Unit,
) {
    val csColors = modeToTheme(mode, primaryColor)
    val globalTransition = rememberInfiniteTransition(label = "GlobalSharedTransition")
    CompositionLocalProvider(LocalCloudStreamColors provides csColors, LocalSharedInfiniteTransition provides globalTransition) {
        MaterialTheme(
            colorScheme = csColors.toMaterial3ColorScheme(),
            content = content,
            typography = AppFont.typography
        )
    }
}