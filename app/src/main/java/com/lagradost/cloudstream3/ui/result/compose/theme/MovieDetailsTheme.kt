package com.lagradost.cloudstream3.ui.result.compose.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

val AppFontFamily: FontFamily = FontFamily.Default

val PrimaryRed = Color(0xFFE50914)
val PrimaryBlack = Color(0xFF000000)
val PrimaryWhite = Color(0xFFFFFFFF)

val Red100 = Color(0xFFEB3942)
val Red200 = Color(0xFFC11119)
val Red300 = Color(0xFFF50723)

val GreenAccent = Color(0xFF46D369)
val YellowAccent = Color(0xFFFFB800)
val OrangeAccent = Color(0xFFFF8C00)

val Grey10 = Color(0xFFE5E5E5)
val Grey50 = Color(0xFFBCBCBC)
val Grey100 = Color(0xFFB3B3B3)
val Grey200 = Color(0xFF808080)
val Grey400 = Color(0xFF414141)
val Grey500 = Color(0xFF3A3A3A)
val Grey600 = Color(0xFF333333)
val Grey700 = Color(0xFF2A2A2A)
val Grey750 = Color(0xFF262626)
val Grey800 = Color(0xFF232323)
val Grey850 = Color(0xFF181818)
val Grey900 = Color(0xFF141414)

val TransparentWhite15 = Color(0x26FFFFFF)
val TransparentWhite20 = Color(0x33FFFFFF)
val TransparentWhite30 = Color(0x4CFFFFFF)
val TransparentWhite50 = Color(0x7FFFFFFF)
val TransparentWhite70 = Color(0xB2FFFFFF)

val TransparentBlack30 = Color(0x4C000000)
val TransparentBlack60 = Color(0x99000000)
val TransparentBlack90 = Color(0xE5000000)

val SurfaceDark = Color(0xFF232323)
val SurfaceElevated = Color(0xFF181818)
val CanvasBackground = Color(0xFF141414)
val BorderSubtle = Color(0x33FFFFFF)
val DividerColor = Color(0x26FFFFFF)

@Immutable
data class MovieDetailsColors(
    val primary: Color = PrimaryRed,
    val onPrimary: Color = PrimaryWhite,
    val background: Color = CanvasBackground,
    val onBackground: Color = PrimaryWhite,
    val surface: Color = SurfaceDark,
    val onSurface: Color = PrimaryWhite,
    val surfaceElevated: Color = SurfaceElevated,
    val border: Color = BorderSubtle,
    val divider: Color = DividerColor,
    val textPrimary: Color = PrimaryWhite,
    val textSecondary: Color = Grey200,
    val textMuted: Color = Grey100,
    val textDimmed: Color = Grey50,
    val greenAccent: Color = GreenAccent,
    val yellowAccent: Color = YellowAccent,
    val orangeAccent: Color = OrangeAccent
)

fun getRatingScoreColor(scoreText: String?): Color {
    if (scoreText.isNullOrBlank()) return GreenAccent
    val text = scoreText.trim()
    if (text.equals("New", ignoreCase = true)) return GreenAccent

    val match = Regex("""(\d+(\.\d+)?)""").find(text)
    if (match != null) {
        val num = match.value.toDoubleOrNull() ?: return GreenAccent
        val isPercentage = text.contains("%") || num > 10.0
        val percentage = if (isPercentage) num else num * 10.0

        return when {
            percentage >= 70.0 -> GreenAccent
            percentage >= 50.0 -> YellowAccent
            else -> Red100
        }
    }

    return GreenAccent
}

@Immutable
data class MovieDetailsTypography(
    val regularCaption2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
    val regularCaption1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp),
    val regularSmallBody: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    val regularBody: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp),
    val regularHeadline2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    val regularHeadline1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 22.sp),
    val regularTitle4: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    val regularTitle3: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 25.sp),
    val regularTitle2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    val regularTitle1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 36.sp),
    val regularLargeTitle: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 50.sp, lineHeight = 64.sp),

    val mediumCaption2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
    val mediumCaption1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    val mediumSmallBody: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    val mediumBody: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    val mediumHeadline2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 30.sp),
    val mediumHeadline1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 25.sp),
    val mediumTitle4: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 22.sp),
    val mediumTitle3: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 31.sp),
    val mediumTitle2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 45.sp),
    val mediumTitle1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 45.sp),
    val mediumLargeTitle: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 33.sp, lineHeight = 42.sp),

    val boldTitle2: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp),
    val boldTitle1: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 62.sp),
    val boldLargeTitle: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 55.sp, lineHeight = 70.sp),

    val headerDisplay: TextStyle = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    val sectionHeader: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = PrimaryRed),
    val logoBebas: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, color = PrimaryRed)
)

@Immutable
data class MovieDetailsDimens(
    val spacingXxs: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingS: Dp = 8.dp,
    val spacingM: Dp = 12.dp,
    val spacingL: Dp = 16.dp,
    val spacingXl: Dp = 20.dp,
    val spacing2Xl: Dp = 24.dp,
    val spacing3Xl: Dp = 32.dp,
    val spacing4Xl: Dp = 40.dp,
    val spacing5Xl: Dp = 48.dp,

    val radiusXs: Dp = 4.dp,
    val radiusS: Dp = 8.dp,
    val radiusM: Dp = 12.dp,
    val radiusL: Dp = 16.dp,
    val radiusXl: Dp = 24.dp,
    val radiusFull: Dp = 999.dp,

    val buttonHeightLarge: Dp = 48.dp,
    val buttonHeightMedium: Dp = 40.dp,
    val buttonHeightSmall: Dp = 32.dp,

    val iconXs: Dp = 14.dp,
    val iconS: Dp = 16.dp,
    val iconM: Dp = 24.dp,
    val iconL: Dp = 32.dp,
    val iconXl: Dp = 40.dp,

    val borderSubtle: Dp = 0.5.dp,
    val borderDefault: Dp = 1.dp,
    val borderFocus: Dp = 2.dp,
    val progressHeight: Dp = 3.5.dp
)

val LocalMovieDetailsColors = staticCompositionLocalOf { MovieDetailsColors() }
val LocalMovieDetailsTypography = staticCompositionLocalOf { MovieDetailsTypography() }
val LocalMovieDetailsDimens = staticCompositionLocalOf { MovieDetailsDimens() }

val MovieDetailsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object MovieDetailsTheme {
    val colors: MovieDetailsColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMovieDetailsColors.current

    val typography: MovieDetailsTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMovieDetailsTypography.current

    val dimens: MovieDetailsDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalMovieDetailsDimens.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}

@Composable
fun rememberMovieDetailsColors(): MovieDetailsColors {
    val context = LocalContext.current
    return remember(context) {
        val primary = try {
            Color(context.colorFromAttribute(R.attr.colorPrimary))
        } catch (_: Throwable) {
            PrimaryRed
        }
        val onPrimary = try {
            Color(context.colorFromAttribute(com.google.android.material.R.attr.colorOnPrimary))
        } catch (_: Throwable) {
            PrimaryWhite
        }
        val background = try {
            Color(context.colorFromAttribute(R.attr.primaryBlackBackground))
        } catch (_: Throwable) {
            CanvasBackground
        }
        val surface = try {
            Color(context.colorFromAttribute(R.attr.primaryGrayBackground))
        } catch (_: Throwable) {
            SurfaceDark
        }
        val surfaceElevated = try {
            Color(context.colorFromAttribute(R.attr.boxItemBackground))
        } catch (_: Throwable) {
            surface
        }
        val textPrimary = try {
            Color(context.colorFromAttribute(R.attr.textColor))
        } catch (_: Throwable) {
            PrimaryWhite
        }
        val textSecondary = try {
            Color(context.colorFromAttribute(R.attr.grayTextColor))
        } catch (_: Throwable) {
            Grey200
        }
        val border = try {
            Color(context.colorFromAttribute(R.attr.iconGrayBackground)).copy(alpha = 0.4f)
        } catch (_: Throwable) {
            BorderSubtle
        }

        MovieDetailsColors(
            primary = primary,
            onPrimary = onPrimary,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            surfaceElevated = surfaceElevated,
            onSurface = textPrimary,
            border = border,
            divider = border,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textSecondary,
            textDimmed = textSecondary,
            greenAccent = GreenAccent,
            yellowAccent = YellowAccent,
            orangeAccent = OrangeAccent
        )
    }
}

@Composable
fun MovieDetailsTheme(
    colors: MovieDetailsColors = rememberMovieDetailsColors(),
    typography: MovieDetailsTypography = MovieDetailsTypography(),
    dimens: MovieDetailsDimens = MovieDetailsDimens(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMovieDetailsColors provides colors,
        LocalMovieDetailsTypography provides typography,
        LocalMovieDetailsDimens provides dimens
    ) {
        MaterialTheme(
            shapes = MovieDetailsShapes,
            content = content
        )
    }
}
