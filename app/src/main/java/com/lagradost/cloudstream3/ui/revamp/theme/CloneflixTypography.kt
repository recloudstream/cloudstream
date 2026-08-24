package com.lagradost.cloudstream3.ui.revamp.theme

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.StyleRes
import androidx.core.content.res.ResourcesCompat
import com.lagradost.cloudstream3.R

object CloneflixTypography {

    const val FONT_FAMILY_NETFLIX_SANS = "Netflix Sans"
    const val FONT_FAMILY_BEBAS_NEUE = "Bebas Neue"

    enum class Weight {
        REGULAR,
        MEDIUM,
        BOLD
    }

    enum class StyleLevel(
        val label: String,
        val sizeSp: Float,
        val lineHeightSp: Float,
        val weight: Weight,
        @StyleRes val styleResId: Int
    ) {
        REGULAR_CAPTION2("Regular / Caption2 / 11px", 11f, 14f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Caption2),
        REGULAR_CAPTION1("Regular / Caption1 / 13px", 13f, 16f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Caption1),
        REGULAR_SMALL_BODY("Regular / SmallBody / 14px", 14f, 18f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_SmallBody),
        REGULAR_BODY("Regular / Body / 16px", 16f, 20f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Body),
        REGULAR_HEADLINE2("Regular / Headline2 / 17px", 17f, 22f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Headline2),
        REGULAR_HEADLINE1("Regular / Headline1 / 18px", 18f, 22f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Headline1),
        REGULAR_TITLE4("Regular / Title4 / 20px", 20f, 30f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Title4),
        REGULAR_TITLE3("Regular / Title3 / 21px", 21f, 25f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Title3),
        REGULAR_TITLE2("Regular / Title2 / 24px", 24f, 30f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Title2),
        REGULAR_TITLE1("Regular / Title1 / 27px", 27f, 36f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_Title1),
        REGULAR_LARGE_TITLE("Regular / LargeTitle / 50px", 50f, 64f, Weight.REGULAR, R.style.TextAppearance_Cloneflix_Regular_LargeTitle),

        MEDIUM_CAPTION2("Medium / Caption2 / 12px", 12f, 15f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Caption2),
        MEDIUM_CAPTION1("Medium / Caption1 / 13px", 13f, 17f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Caption1),
        MEDIUM_SMALL_BODY("Medium / SmallBody / 14px", 14f, 18f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_SmallBody),
        MEDIUM_BODY("Medium / Body / 16px", 16f, 24f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Body),
        MEDIUM_HEADLINE2("Medium / Headline2 / 20px", 20f, 30f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Headline2),
        MEDIUM_HEADLINE1("Medium / Headline1 / 21px", 21f, 25f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Headline1),
        MEDIUM_TITLE4("Medium / Title4 / 22px", 22f, 22f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Title4),
        MEDIUM_TITLE3("Medium / Title3 / 24px", 24f, 31f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Title3),
        MEDIUM_TITLE2("Medium / Title2 / 28px", 28f, 45f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Title2),
        MEDIUM_TITLE1("Medium / Title1 / 30px", 30f, 45f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_Title1),
        MEDIUM_LARGE_TITLE("Medium / LargeTitle / 33px", 33f, 42f, Weight.MEDIUM, R.style.TextAppearance_Cloneflix_Medium_LargeTitle),

        BOLD_TITLE2("Bold / Title2 / 20px", 20f, 30f, Weight.BOLD, R.style.TextAppearance_Cloneflix_Bold_Title2),
        BOLD_TITLE1("Bold / Title1 / 48px", 48f, 62f, Weight.BOLD, R.style.TextAppearance_Cloneflix_Bold_Title1),
        BOLD_LARGE_TITLE("Bold / LargeTitle / 55px", 55f, 70f, Weight.BOLD, R.style.TextAppearance_Cloneflix_Bold_LargeTitle),

        LOGO_BEBAS("Logo – Bebas Neue / 32px", 32f, 38f, Weight.BOLD, R.style.TextAppearance_Cloneflix_LogoBebas),
        HEADER_DISPLAY("Display Header / 40px", 40f, 48f, Weight.BOLD, R.style.TextAppearance_Cloneflix_HeaderDisplay);

        fun getCategoryName(): String = when (weight) {
            Weight.REGULAR -> "Regular Styles"
            Weight.MEDIUM -> "Medium Styles"
            Weight.BOLD -> if (this == LOGO_BEBAS) "Logo & Display" else "Bold Styles"
        }
    }

    fun getFontTypeface(context: Context, weight: Weight): Typeface {
        val base = try {
            ResourcesCompat.getFont(context, R.font.netflix_sans)
        } catch (_: Exception) {
            null
        } ?: Typeface.SANS_SERIF

        return when (weight) {
            Weight.REGULAR -> Typeface.create(base, Typeface.NORMAL)
            Weight.MEDIUM -> Typeface.create(base, Typeface.BOLD)
            Weight.BOLD -> Typeface.create(base, Typeface.BOLD)
        }
    }
}
