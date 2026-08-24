package com.lagradost.cloudstream3.ui.revamp.theme

import android.graphics.Color
import androidx.annotation.ColorInt

object CloneflixColors {

    const val PRIMARY_RED_HEX = "#E50914"
    const val PRIMARY_BLACK_HEX = "#000000"
    const val PRIMARY_WHITE_HEX = "#FFFFFF"

    @ColorInt val PrimaryRed = Color.parseColor(PRIMARY_RED_HEX)
    @ColorInt val PrimaryBlack = Color.parseColor(PRIMARY_BLACK_HEX)
    @ColorInt val PrimaryWhite = Color.parseColor(PRIMARY_WHITE_HEX)

    const val RED_100_HEX = "#EB3942"
    const val RED_200_HEX = "#C11119"
    const val RED_300_HEX = "#F50723"

    @ColorInt val Red100 = Color.parseColor(RED_100_HEX)
    @ColorInt val Red200 = Color.parseColor(RED_200_HEX)
    @ColorInt val Red300 = Color.parseColor(RED_300_HEX)

    const val BLUE_100_HEX = "#0071EB"
    const val BLUE_200_HEX = "#448EF4"
    const val BLUE_300_HEX = "#54B9C5"

    @ColorInt val Blue100 = Color.parseColor(BLUE_100_HEX)
    @ColorInt val Blue200 = Color.parseColor(BLUE_200_HEX)
    @ColorInt val Blue300 = Color.parseColor(BLUE_300_HEX)

    const val GREEN_HEX = "#46D369"
    @ColorInt val Green = Color.parseColor(GREEN_HEX)

    const val GREY_10_HEX = "#E5E5E5"
    const val GREY_20_HEX = "#DCDCDC"
    const val GREY_25_HEX = "#D2D2D2"
    const val GREY_50_HEX = "#BCBCBC"
    const val GREY_100_HEX = "#B3B3B3"
    const val GREY_150_HEX = "#979797"
    const val GREY_200_HEX = "#808080"
    const val GREY_250_HEX = "#777777"
    const val GREY_300_T40_HEX = "#666D6D6E"
    const val GREY_300_T70_HEX = "#B26D6D6E"
    const val GREY_350_HEX = "#545454"
    const val GREY_400_HEX = "#414141"
    const val GREY_450_HEX = "#404040"
    const val GREY_500_HEX = "#3A3A3A"
    const val GREY_550_HEX = "#363636"
    const val GREY_600_T60_HEX = "#99333333"
    const val GREY_600_HEX = "#333333"
    const val GREY_650_HEX = "#2F2F2F"
    const val GREY_700_HEX = "#2A2A2A"
    const val GREY_750_HEX = "#262626"
    const val GREY_800_HEX = "#232323"
    const val GREY_850_HEX = "#181818"
    const val GREY_900_HEX = "#141414"

    @ColorInt val Grey10 = Color.parseColor(GREY_10_HEX)
    @ColorInt val Grey20 = Color.parseColor(GREY_20_HEX)
    @ColorInt val Grey25 = Color.parseColor(GREY_25_HEX)
    @ColorInt val Grey50 = Color.parseColor(GREY_50_HEX)
    @ColorInt val Grey100 = Color.parseColor(GREY_100_HEX)
    @ColorInt val Grey150 = Color.parseColor(GREY_150_HEX)
    @ColorInt val Grey200 = Color.parseColor(GREY_200_HEX)
    @ColorInt val Grey250 = Color.parseColor(GREY_250_HEX)
    @ColorInt val Grey300T40 = Color.parseColor(GREY_300_T40_HEX)
    @ColorInt val Grey300T70 = Color.parseColor(GREY_300_T70_HEX)
    @ColorInt val Grey350 = Color.parseColor(GREY_350_HEX)
    @ColorInt val Grey400 = Color.parseColor(GREY_400_HEX)
    @ColorInt val Grey450 = Color.parseColor(GREY_450_HEX)
    @ColorInt val Grey500 = Color.parseColor(GREY_500_HEX)
    @ColorInt val Grey550 = Color.parseColor(GREY_550_HEX)
    @ColorInt val Grey600T60 = Color.parseColor(GREY_600_T60_HEX)
    @ColorInt val Grey600 = Color.parseColor(GREY_600_HEX)
    @ColorInt val Grey650 = Color.parseColor(GREY_650_HEX)
    @ColorInt val Grey700 = Color.parseColor(GREY_700_HEX)
    @ColorInt val Grey750 = Color.parseColor(GREY_750_HEX)
    @ColorInt val Grey800 = Color.parseColor(GREY_800_HEX)
    @ColorInt val Grey850 = Color.parseColor(GREY_850_HEX)
    @ColorInt val Grey900 = Color.parseColor(GREY_900_HEX)

    const val TRANSPARENT_WHITE_15_HEX = "#26FFFFFF"
    const val TRANSPARENT_WHITE_20_HEX = "#33FFFFFF"
    const val TRANSPARENT_WHITE_30_HEX = "#4CFFFFFF"
    const val TRANSPARENT_WHITE_35_HEX = "#59FFFFFF"
    const val TRANSPARENT_WHITE_50_HEX = "#7FFFFFFF"
    const val TRANSPARENT_WHITE_70_HEX = "#B2FFFFFF"

    @ColorInt val TransparentWhite15 = Color.parseColor(TRANSPARENT_WHITE_15_HEX)
    @ColorInt val TransparentWhite20 = Color.parseColor(TRANSPARENT_WHITE_20_HEX)
    @ColorInt val TransparentWhite30 = Color.parseColor(TRANSPARENT_WHITE_30_HEX)
    @ColorInt val TransparentWhite35 = Color.parseColor(TRANSPARENT_WHITE_35_HEX)
    @ColorInt val TransparentWhite50 = Color.parseColor(TRANSPARENT_WHITE_50_HEX)
    @ColorInt val TransparentWhite70 = Color.parseColor(TRANSPARENT_WHITE_70_HEX)

    const val TRANSPARENT_BLACK_30_HEX = "#4C000000"
    const val TRANSPARENT_BLACK_60_HEX = "#99000000"
    const val TRANSPARENT_BLACK_90_HEX = "#E5000000"

    @ColorInt val TransparentBlack30 = Color.parseColor(TRANSPARENT_BLACK_30_HEX)
    @ColorInt val TransparentBlack60 = Color.parseColor(TRANSPARENT_BLACK_60_HEX)
    @ColorInt val TransparentBlack90 = Color.parseColor(TRANSPARENT_BLACK_90_HEX)

    data class ColorToken(
        val category: String,
        val name: String,
        val hex: String,
        val rgbDescription: String,
        @ColorInt val colorInt: Int,
        val isDark: Boolean = true
    )

    fun getAllTokens(): List<ColorToken> = listOf(
        ColorToken("Primary", "Primary / Red", PRIMARY_RED_HEX, "RGB (229, 9, 20)", PrimaryRed),
        ColorToken("Primary", "Primary / Black", PRIMARY_BLACK_HEX, "RGB (0, 0, 0)", PrimaryBlack),
        ColorToken("Primary", "Primary / White", PRIMARY_WHITE_HEX, "RGB (255, 255, 255)", PrimaryWhite, isDark = false),

        ColorToken("Secondary", "Secondary / Red-100", RED_100_HEX, "RGB (235, 57, 66)", Red100),
        ColorToken("Secondary", "Secondary / Red-200", RED_200_HEX, "RGB (193, 17, 25)", Red200),
        ColorToken("Secondary", "Secondary / Red-300", RED_300_HEX, "RGB (245, 7, 35)", Red300),
        ColorToken("Secondary", "Secondary / Blue-100", BLUE_100_HEX, "RGB (0, 113, 235)", Blue100),
        ColorToken("Secondary", "Secondary / Blue-200", BLUE_200_HEX, "RGB (68, 142, 244)", Blue200),
        ColorToken("Secondary", "Secondary / Blue-300", BLUE_300_HEX, "RGB (84, 185, 197)", Blue300),
        ColorToken("Secondary", "Secondary / Green", GREEN_HEX, "RGB (70, 211, 105)", Green),

        ColorToken("Neutral Greys", "Grey-10", GREY_10_HEX, "RGB (229, 229, 229)", Grey10, isDark = false),
        ColorToken("Neutral Greys", "Grey-20", GREY_20_HEX, "RGB (220, 220, 220)", Grey20, isDark = false),
        ColorToken("Neutral Greys", "Grey-25", GREY_25_HEX, "RGB (210, 210, 210)", Grey25, isDark = false),
        ColorToken("Neutral Greys", "Grey-50", GREY_50_HEX, "RGB (188, 188, 188)", Grey50, isDark = false),
        ColorToken("Neutral Greys", "Grey-100", GREY_100_HEX, "RGB (179, 179, 179)", Grey100),
        ColorToken("Neutral Greys", "Grey-150", GREY_150_HEX, "RGB (151, 151, 151)", Grey150),
        ColorToken("Neutral Greys", "Grey-200", GREY_200_HEX, "RGB (128, 128, 128)", Grey200),
        ColorToken("Neutral Greys", "Grey-250", GREY_250_HEX, "RGB (119, 119, 119)", Grey250),
        ColorToken("Neutral Greys", "Grey-300T40", GREY_300_T40_HEX, "RGB (109, 109, 110, 40%)", Grey300T40),
        ColorToken("Neutral Greys", "Grey-300T70", GREY_300_T70_HEX, "RGB (109, 109, 110, 70%)", Grey300T70),
        ColorToken("Neutral Greys", "Grey-350", GREY_350_HEX, "RGB (84, 84, 84)", Grey350),
        ColorToken("Neutral Greys", "Grey-400", GREY_400_HEX, "RGB (65, 65, 65)", Grey400),
        ColorToken("Neutral Greys", "Grey-450", GREY_450_HEX, "RGB (64, 64, 64)", Grey450),
        ColorToken("Neutral Greys", "Grey-500", GREY_500_HEX, "RGB (58, 58, 58)", Grey500),
        ColorToken("Neutral Greys", "Grey-550", GREY_550_HEX, "RGB (54, 54, 54)", Grey550),
        ColorToken("Neutral Greys", "Grey-600T60", GREY_600_T60_HEX, "RGB (51, 51, 51, 60%)", Grey600T60),
        ColorToken("Neutral Greys", "Grey-600", GREY_600_HEX, "RGB (51, 51, 51)", Grey600),
        ColorToken("Neutral Greys", "Grey-650", GREY_650_HEX, "RGB (47, 47, 47)", Grey650),
        ColorToken("Neutral Greys", "Grey-700", GREY_700_HEX, "RGB (42, 42, 42)", Grey700),
        ColorToken("Neutral Greys", "Grey-750", GREY_750_HEX, "RGB (38, 38, 38)", Grey750),
        ColorToken("Neutral Greys", "Grey-800", GREY_800_HEX, "RGB (35, 35, 35)", Grey800),
        ColorToken("Neutral Greys", "Grey-850", GREY_850_HEX, "RGB (24, 24, 24)", Grey850),
        ColorToken("Neutral Greys", "Grey-900", GREY_900_HEX, "RGB (20, 20, 20)", Grey900),

        ColorToken("Transparent White", "TransparentWhite-15", TRANSPARENT_WHITE_15_HEX, "RGB (255, 255, 255, 15%)", TransparentWhite15),
        ColorToken("Transparent White", "TransparentWhite-20", TRANSPARENT_WHITE_20_HEX, "RGB (255, 255, 255, 20%)", TransparentWhite20),
        ColorToken("Transparent White", "TransparentWhite-30", TRANSPARENT_WHITE_30_HEX, "RGB (255, 255, 255, 30%)", TransparentWhite30),
        ColorToken("Transparent White", "TransparentWhite-35", TRANSPARENT_WHITE_35_HEX, "RGB (255, 255, 255, 35%)", TransparentWhite35),
        ColorToken("Transparent White", "TransparentWhite-50", TRANSPARENT_WHITE_50_HEX, "RGB (255, 255, 255, 50%)", TransparentWhite50),
        ColorToken("Transparent White", "TransparentWhite-70", TRANSPARENT_WHITE_70_HEX, "RGB (255, 255, 255, 70%)", TransparentWhite70),

        ColorToken("Transparent Black", "TransparentBlack-30", TRANSPARENT_BLACK_30_HEX, "RGB (0, 0, 0, 30%)", TransparentBlack30),
        ColorToken("Transparent Black", "TransparentBlack-60", TRANSPARENT_BLACK_60_HEX, "RGB (0, 0, 0, 60%)", TransparentBlack60),
        ColorToken("Transparent Black", "TransparentBlack-90", TRANSPARENT_BLACK_90_HEX, "RGB (0, 0, 0, 90%)", TransparentBlack90)
    )
}
