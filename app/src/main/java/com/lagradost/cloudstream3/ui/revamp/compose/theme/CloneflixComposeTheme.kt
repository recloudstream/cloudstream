package com.lagradost.cloudstream3.ui.revamp.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.R

val CloneflixFontFamily = FontFamily(
    Font(R.font.netflix_sans, FontWeight.Normal),
    Font(R.font.netflix_sans, FontWeight.Medium),
    Font(R.font.netflix_sans, FontWeight.Bold)
)

val PrimaryRed = Color(0xFFE50914)
val PrimaryBlack = Color(0xFF000000)
val PrimaryWhite = Color(0xFFFFFFFF)

val Red100 = Color(0xFFEB3942)
val Red200 = Color(0xFFC11119)
val Red300 = Color(0xFFF50723)

val Blue100 = Color(0xFF0071EB)
val Blue200 = Color(0xFF448EF4)
val Blue300 = Color(0xFF54B9C5)

val GreenAccent = Color(0xFF46D369)

val Grey10 = Color(0xFFE5E5E5)
val Grey20 = Color(0xFFDCDCDC)
val Grey25 = Color(0xFFD2D2D2)
val Grey50 = Color(0xFFBCBCBC)
val Grey100 = Color(0xFFB3B3B3)
val Grey150 = Color(0xFF979797)
val Grey200 = Color(0xFF808080)
val Grey250 = Color(0xFF777777)
val Grey300T40 = Color(0x666D6D6E)
val Grey300T70 = Color(0xB26D6D6E)
val Grey350 = Color(0xFF545454)
val Grey400 = Color(0xFF414141)
val Grey450 = Color(0xFF404040)
val Grey500 = Color(0xFF3A3A3A)
val Grey550 = Color(0xFF363636)
val Grey600T60 = Color(0x99333333)
val Grey600 = Color(0xFF333333)
val Grey650 = Color(0xFF2F2F2F)
val Grey700 = Color(0xFF2A2A2A)
val Grey750 = Color(0xFF262626)
val Grey800 = Color(0xFF232323)
val Grey850 = Color(0xFF181818)
val Grey900 = Color(0xFF141414)

val TransparentWhite15 = Color(0x26FFFFFF)
val TransparentWhite20 = Color(0x33FFFFFF)
val TransparentWhite30 = Color(0x4CFFFFFF)
val TransparentWhite35 = Color(0x59FFFFFF)
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
data class CloneflixColors(
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
    val redAccent100: Color = Red100,
    val redAccent200: Color = Red200,
    val redAccent300: Color = Red300,
    val blueAccent100: Color = Blue100,
    val blueAccent200: Color = Blue200,
    val blueAccent300: Color = Blue300,
    val greenAccent: Color = GreenAccent
)

@Immutable
data class CloneflixTypography(
    val regularCaption2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
    val regularCaption1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp),
    val regularSmallBody: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    val regularBody: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp),
    val regularHeadline2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    val regularHeadline1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 22.sp),
    val regularTitle4: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    val regularTitle3: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 25.sp),
    val regularTitle2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    val regularTitle1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 36.sp),
    val regularLargeTitle: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Normal, fontSize = 50.sp, lineHeight = 64.sp),

    val mediumCaption2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
    val mediumCaption1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    val mediumSmallBody: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    val mediumBody: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    val mediumHeadline2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 30.sp),
    val mediumHeadline1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 25.sp),
    val mediumTitle4: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 22.sp),
    val mediumTitle3: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 31.sp),
    val mediumTitle2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 45.sp),
    val mediumTitle1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 45.sp),
    val mediumLargeTitle: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Medium, fontSize = 33.sp, lineHeight = 42.sp),

    val boldTitle2: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp),
    val boldTitle1: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 62.sp),
    val boldLargeTitle: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Bold, fontSize = 55.sp, lineHeight = 70.sp),

    val headerDisplay: TextStyle = TextStyle(fontFamily = CloneflixFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    val sectionHeader: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = PrimaryRed),
    val logoBebas: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, color = PrimaryRed)
)

@Immutable
data class CloneflixDimens(
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
    val spacing6Xl: Dp = 64.dp,
    val spacing7Xl: Dp = 80.dp,

    val radiusXs: Dp = 4.dp,
    val radiusS: Dp = 8.dp,
    val radiusM: Dp = 12.dp,
    val radiusL: Dp = 16.dp,
    val radiusXl: Dp = 24.dp,
    val radiusFull: Dp = 999.dp,

    val headerIconSize: Dp = 80.dp,
    val headerIconStroke: Dp = 1.4.dp,
    val buttonHeightLarge: Dp = 48.dp,
    val buttonHeightMedium: Dp = 40.dp,
    val buttonHeightSmall: Dp = 32.dp,
    val inputHeight: Dp = 52.dp,
    val swatchSize: Dp = 56.dp,
    val dividerThickness: Dp = 1.dp,

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

val LocalCloneflixColors = staticCompositionLocalOf { CloneflixColors() }
val LocalCloneflixTypography = staticCompositionLocalOf { CloneflixTypography() }
val LocalCloneflixDimens = staticCompositionLocalOf { CloneflixDimens() }

val CloneflixShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object CloneflixTheme {
    val colors: CloneflixColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCloneflixColors.current

    val typography: CloneflixTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalCloneflixTypography.current

    val dimens: CloneflixDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalCloneflixDimens.current

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}

@Composable
fun CloneflixTheme(
    colors: CloneflixColors = CloneflixColors(),
    typography: CloneflixTypography = CloneflixTypography(),
    dimens: CloneflixDimens = CloneflixDimens(),
    content: @Composable () -> Unit
) {
    val materialColors = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onSurface
    )

    CompositionLocalProvider(
        LocalCloneflixColors provides colors,
        LocalCloneflixTypography provides typography,
        LocalCloneflixDimens provides dimens
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            shapes = CloneflixShapes,
            content = content
        )
    }
}
