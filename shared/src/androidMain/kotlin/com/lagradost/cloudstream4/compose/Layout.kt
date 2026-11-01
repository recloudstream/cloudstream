package com.lagradost.cloudstream4.compose

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.cloudstream4.compose.DeviceLayout.Companion.LocalLayout

@JvmInline
@Immutable
value class DeviceLayout(private val value: Int) {
    infix fun or(other: DeviceLayout) =
        DeviceLayout(value or other.value)

    internal infix fun and(other: DeviceLayout) = (value and other.value) != 0

    companion object {
        val LocalLayout: ProvidableCompositionLocal<DeviceLayout> = staticCompositionLocalOf { DeviceLayout(-1) }
        fun isAutoTv(context: Context): Boolean {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            // AFT = Fire TV
            val model = Build.MODEL.lowercase()
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
                "AFT"
            ) || model.contains("firestick") || model.contains("fire tv") || model.contains("chromecast")
        }
        internal fun layoutToFlag(context : Context, from : Int): DeviceLayout {
            return when(from) {
                -1 -> if (isAutoTv(context)) TV else PHONE
                0 -> PHONE
                1 -> TV
                2 -> EMULATOR
                else -> PHONE
            }
        }
    }
}

val PHONE = DeviceLayout(0b00001)
val TV = DeviceLayout(0b00010)
val EMULATOR = DeviceLayout(0b00100)

@Composable
fun isLayout(flags: DeviceLayout) : Boolean = LocalLayout.current and flags