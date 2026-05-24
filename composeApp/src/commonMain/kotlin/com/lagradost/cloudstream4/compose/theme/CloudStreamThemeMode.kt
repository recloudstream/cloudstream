package com.lagradost.cloudstream4.compose.theme

enum class CloudStreamThemeMode {
    /** "Black" standard dark, #111111 backgrounds */
    Dark,
    /** "Amoled" / "AmoledLight" pure black (#000000) */
    Amoled,
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
