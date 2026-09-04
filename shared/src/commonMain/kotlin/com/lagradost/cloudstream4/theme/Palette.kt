package com.lagradost.cloudstream4.theme

import androidx.compose.ui.graphics.Color

enum class CloudStreamPrimaryColor(val color: Color) {
    NORMAL(Color(0xFF3D50FA)),
    BLUE(Color(0xFF5664B7)),
    PURPLE(Color(0xFF6200EA)),
    GREEN(Color(0xFF00BFA5)),
    GREEN_APPLE(Color(0xFF48E484)),
    RED(Color(0xFFD50000)),
    BANANA(Color(0xFFE4D448)),
    PARTY(Color(0xFFEA596E)),
    PINK(Color(0xFFFF1493)),
    CARNATION_PINK(Color(0xFFBD5DA5)),
    MAROON(Color(0xFF451010)),
    DARK_GREEN(Color(0xFF004500)),
    NAVY_BLUE(Color(0xFF000080)),
    GREY(Color(0xFF515151)),
    WHITE(Color(0xFFFFFFFF)),
    BROWN(Color(0xFF622C00)),
    ORANGE(Color(0xFFCE8500)),
    DANDELION_YELLOW(Color(0xFFF5BB00)),
    COOL_BLUE(Color(0xFF408CAC)),
    LAVENDER(Color(0xFF6F55AF)),
    DYNAMIC(Color(0xFF3D50FA)),
    DYNAMIC_TWO(Color(0xFF3D50FA)),
}


internal object CloudStreamPalette {
    // Default dark (AppTheme / Black)
    val Primary = Color(0xFF3D50FA)
    val PrimaryDark = Color(0xFF3700B3)
    val Ongoing = Color(0xFFF53B66)

    val DarkPrimaryGrayBg = Color(0xFF2B2C30)
    val DarkBlackBg = Color(0xFF111111)
    val DarkIconGrayBg = Color(0xFF1C1C20)
    val DarkBoxItemBg = Color(0xFF161616)
    val DarkText = Color(0xFFE9EAEE)
    val DarkGrayText = Color(0xFF9BA0A4)
    val DarkIcon = Color(0xFF9BA0A6)

    // Amoled
    val AmoledBlack = Color(0xFF000000)
    val AmoledNearBlack = Color(0xFF121212)

    // Light
    val LightPrimaryGrayBg = Color(0xFFF1F1F1)
    val LightBlackBg = Color(0xFFFFFFFF)
    val LightIconGrayBg = Color(0xFFEEEEEE)
    val LightBoxItemBg = Color(0xFFEEEEEE)
    val LightText = Color(0xFF202125)
    val LightGrayText = Color(0xFF5F6267)
    val LightIcon = Color(0xFF5F6267)

    // Dracula
    val DraculaPrimaryGrayBg = Color(0xFF414450)
    val DraculaBlackBg = Color(0xFF282A36)
    val DraculaIconGrayBg = Color(0xFF44475A)
    val DraculaBoxItemBg = Color(0xFF373844)
    val DraculaText = Color(0xFFF8F8F2)
    val DraculaGrayText = Color(0xFF6272A4)
    val DraculaIcon = Color(0xFF6272A4)

    // Lavender Dreams
    val LavenderPrimaryGrayBg = Color(0xFFF7EEFC)
    val LavenderBlackBg = Color(0xFFFDF0FB)
    val LavenderIconGrayBg = Color(0xFFB794F6)
    val LavenderBoxItemBg = Color(0xFFF8F5FF)
    val LavenderText = Color(0xFF2D1B47)
    val LavenderGrayText = Color(0xFF9AB3FF)
    val LavenderIcon = Color(0xFF7C3AED)

    // Silent Blue
    val SilentBluePrimaryGrayBg = Color(0xFF282F49)
    val SilentBlueBlackBg = Color(0xFF151A30)
    val SilentBlueIconGrayBg = Color(0xFF3A446A)
    val SilentBlueBoxItemBg = Color(0xFF3A446A)
    val SilentBlueText = Color(0xFFE0E1F3)
    val SilentBlueGrayText = Color(0xFF7B83B0)
    val SilentBlueIcon = Color(0xFF7B83B0)
}
