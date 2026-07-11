package com.lagradost.cloudstream4.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Maps to the XML custom attrs declared in attrs.xml:
 * TODO: Remove this comment when we migrate fully
 *  and attrs.xml will no longer be used at all.
 *
 * | XML ?attr                | Here               |
 * |--------------------------|--------------------|
 * | primaryBlackBackground   | [background]       |
 * | primaryGrayBackground    | [surfaceVariant]   |
 * | iconGrayBackground       | [surface]          |
 * | boxItemBackground        | [surfaceContainer] |
 * | textColor                | [onBackground]     |
 * | grayTextColor            | [onSurfaceVariant] |
 * | iconColor                | [icon]             |
 * | colorPrimary             | [primary]          |
 * | colorOngoing             | [ongoing]          |
 *
 * All fields are [MutableState] so Compose recomposes automatically
 * if the scheme is swapped at runtime (e.g. user changes theme without restart).
 */
@Stable
class CloudStreamColorScheme(
    background: Color,
    surfaceVariant: Color,
    surface: Color,
    surfaceContainer: Color,
    onBackground: Color,
    onSurfaceVariant: Color,
    icon: Color,
    primary: Color,
    ongoing: Color,
    isLight: Boolean,
) {
    var background by mutableStateOf(background)
    var surfaceVariant by mutableStateOf(surfaceVariant)
    var surface by mutableStateOf(surface)
    var surfaceContainer by mutableStateOf(surfaceContainer)
    var onBackground by mutableStateOf(onBackground)
    var onSurfaceVariant by mutableStateOf(onSurfaceVariant)
    var icon by mutableStateOf(icon)
    var primary by mutableStateOf(primary)
    var ongoing by mutableStateOf(ongoing)
    var isLight by mutableStateOf(isLight)

    fun copy(
        background: Color = this.background,
        surfaceVariant: Color = this.surfaceVariant,
        surface: Color = this.surface,
        surfaceContainer: Color = this.surfaceContainer,
        onBackground: Color = this.onBackground,
        onSurfaceVariant: Color = this.onSurfaceVariant,
        icon: Color = this.icon,
        primary: Color = this.primary,
        ongoing: Color = this.ongoing,
        isLight: Boolean = this.isLight,
    ) = CloudStreamColorScheme(
        background, surfaceVariant, surface, surfaceContainer,
        onBackground, onSurfaceVariant, icon, primary, ongoing, isLight,
    )
}

internal fun darkScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.DarkBlackBg,
    surfaceVariant = CloudStreamPalette.DarkPrimaryGrayBg,
    surface = CloudStreamPalette.DarkIconGrayBg,
    surfaceContainer = CloudStreamPalette.DarkBoxItemBg,
    onBackground = CloudStreamPalette.DarkText,
    onSurfaceVariant = CloudStreamPalette.DarkGrayText,
    icon = CloudStreamPalette.DarkIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)

internal fun amoledScheme() = darkScheme().copy(
    background = CloudStreamPalette.AmoledBlack,
    surface = CloudStreamPalette.AmoledBlack,
    surfaceVariant = CloudStreamPalette.AmoledBlack,
    surfaceContainer = CloudStreamPalette.AmoledBlack,
)

internal fun lightScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.LightBlackBg,
    surfaceVariant = CloudStreamPalette.LightPrimaryGrayBg,
    surface = CloudStreamPalette.LightIconGrayBg,
    surfaceContainer = CloudStreamPalette.LightBoxItemBg,
    onBackground = CloudStreamPalette.LightText,
    onSurfaceVariant = CloudStreamPalette.LightGrayText,
    icon = CloudStreamPalette.LightIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = true,
)

internal fun draculaScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.DraculaBlackBg,
    surfaceVariant = CloudStreamPalette.DraculaPrimaryGrayBg,
    surface = CloudStreamPalette.DraculaIconGrayBg,
    surfaceContainer = CloudStreamPalette.DraculaBoxItemBg,
    onBackground = CloudStreamPalette.DraculaText,
    onSurfaceVariant = CloudStreamPalette.DraculaGrayText,
    icon = CloudStreamPalette.DraculaIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)

internal fun lavenderScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.LavenderBlackBg,
    surfaceVariant = CloudStreamPalette.LavenderPrimaryGrayBg,
    surface = CloudStreamPalette.LavenderIconGrayBg,
    surfaceContainer = CloudStreamPalette.LavenderBoxItemBg,
    onBackground = CloudStreamPalette.LavenderText,
    onSurfaceVariant = CloudStreamPalette.LavenderGrayText,
    icon = CloudStreamPalette.LavenderIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = true,
)

internal fun silentBlueScheme() = CloudStreamColorScheme(
    background = CloudStreamPalette.SilentBlueBlackBg,
    surfaceVariant = CloudStreamPalette.SilentBluePrimaryGrayBg,
    surface = CloudStreamPalette.SilentBlueIconGrayBg,
    surfaceContainer = CloudStreamPalette.SilentBlueBoxItemBg,
    onBackground = CloudStreamPalette.SilentBlueText,
    onSurfaceVariant = CloudStreamPalette.SilentBlueGrayText,
    icon = CloudStreamPalette.SilentBlueIcon,
    primary = CloudStreamPalette.Primary,
    ongoing = CloudStreamPalette.Ongoing,
    isLight = false,
)
