package com.lagradost.cloudstream4.settings

import com.lagradost.cloudstream4.theme.CloudStreamPrimaryColor
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode

/** Desktop version of perfToMode in shared/androidMain, without Material You */
fun desktopThemeMode(value: String?): CloudStreamThemeMode = when (value) {
    "System" -> CloudStreamThemeMode.FollowSystem
    "Black" -> CloudStreamThemeMode.Dark
    "Light" -> CloudStreamThemeMode.Light
    "Amoled" -> CloudStreamThemeMode.Amoled
    "AmoledLight" -> CloudStreamThemeMode.AmoledLight
    "Dracula" -> CloudStreamThemeMode.Dracula
    "Lavender" -> CloudStreamThemeMode.Lavender
    "SilentBlue" -> CloudStreamThemeMode.SilentBlue
    // "Black" and the default: the Prime Video dark
    else -> CloudStreamThemeMode.Dark
}

/** Desktop version of perfToColor in shared/androidMain, without Material You */
fun desktopPrimaryColor(value: String?): CloudStreamPrimaryColor = when (value) {
    "Blue" -> CloudStreamPrimaryColor.BLUE
    "Purple" -> CloudStreamPrimaryColor.PURPLE
    "Green" -> CloudStreamPrimaryColor.GREEN
    "GreenApple" -> CloudStreamPrimaryColor.GREEN_APPLE
    "Red" -> CloudStreamPrimaryColor.RED
    "Banana" -> CloudStreamPrimaryColor.BANANA
    "Party" -> CloudStreamPrimaryColor.PARTY
    "Pink" -> CloudStreamPrimaryColor.PINK
    "CarnationPink" -> CloudStreamPrimaryColor.CARNATION_PINK
    "Maroon" -> CloudStreamPrimaryColor.MAROON
    "DarkGreen" -> CloudStreamPrimaryColor.DARK_GREEN
    "NavyBlue" -> CloudStreamPrimaryColor.NAVY_BLUE
    "Grey" -> CloudStreamPrimaryColor.GREY
    "White" -> CloudStreamPrimaryColor.WHITE
    "Brown" -> CloudStreamPrimaryColor.BROWN
    "Orange" -> CloudStreamPrimaryColor.ORANGE
    "DandelionYellow" -> CloudStreamPrimaryColor.DANDELION_YELLOW
    "CoolBlue" -> CloudStreamPrimaryColor.COOL_BLUE
    "Lavender" -> CloudStreamPrimaryColor.LAVENDER
    else -> CloudStreamPrimaryColor.NORMAL
}
