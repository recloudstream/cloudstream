package com.lagradost.cloudstream4

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/** The dwmapi function the title bar needs, see dwmapi.h */
@Suppress("FunctionName")
private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

private val dwmapi: DwmApi? by lazy {
    if (!System.getProperty("os.name").startsWith("Windows")) return@lazy null
    runCatching { Native.load("dwmapi", DwmApi::class.java) }
        .onFailure { println("ERROR DwmApi: dwmapi could not be loaded: $it") }
        .getOrNull()
}

/**
 * Paints the window's title bar and border, which Windows draws, in the theme's colours, so the
 * frame matches the page inside it instead of staying white. Windows 11 takes the exact colours,
 * Windows 10 only switches the title bar to dark for a dark theme.
 */
object WindowsTitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    fun paint(window: Window, background: Color, text: Color) {
        // Also what shows for a moment where the window grows, before the page is drawn there again
        window.background = java.awt.Color(background.toArgb(), false)
        val dwm = dwmapi ?: return
        val hwnd = Native.getWindowPointer(window) ?: return
        fun set(attribute: Int, value: Int) = dwm.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)
        set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (background.luminance() < 0.5f) 1 else 0)
        set(DWMWA_CAPTION_COLOR, background.colorRef())
        set(DWMWA_BORDER_COLOR, background.colorRef())
        set(DWMWA_TEXT_COLOR, text.colorRef())
    }

    /** A COLORREF, 0x00BBGGRR */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
