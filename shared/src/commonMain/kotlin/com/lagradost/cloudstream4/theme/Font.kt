package com.lagradost.cloudstream4.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.google_sans
import org.jetbrains.compose.resources.Font

object AppFont {
    val googleSans @Composable get() = FontFamily(
        Font(Res.font.google_sans),
        Font(Res.font.google_sans, style = FontStyle.Italic),
        Font(Res.font.google_sans, FontWeight.Medium),
        Font(Res.font.google_sans, FontWeight.Medium, style = FontStyle.Italic),
        Font(Res.font.google_sans, FontWeight.Bold),
        Font(Res.font.google_sans, FontWeight.Bold, style = FontStyle.Italic)
    )

    private val defaultTypography = androidx.compose.material3.Typography()
    val typography @Composable get() =
        googleSans.let { fontFamily ->
            Typography(
                displayLarge = defaultTypography.displayLarge.copy(fontFamily = fontFamily),
                displayMedium = defaultTypography.displayMedium.copy(fontFamily = fontFamily),
                displaySmall = defaultTypography.displaySmall.copy(fontFamily = fontFamily),

                headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = fontFamily),
                headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = fontFamily),
                headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = fontFamily),

                titleLarge = defaultTypography.titleLarge.copy(fontFamily = fontFamily),
                titleMedium = defaultTypography.titleMedium.copy(fontFamily = fontFamily),
                titleSmall = defaultTypography.titleSmall.copy(fontFamily = fontFamily),

                bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = fontFamily),
                bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = fontFamily),
                bodySmall = defaultTypography.bodySmall.copy(fontFamily = fontFamily),

                labelLarge = defaultTypography.labelLarge.copy(fontFamily = fontFamily),
                labelMedium = defaultTypography.labelMedium.copy(fontFamily = fontFamily),
                labelSmall = defaultTypography.labelSmall.copy(fontFamily = fontFamily)
            )
        }
}

