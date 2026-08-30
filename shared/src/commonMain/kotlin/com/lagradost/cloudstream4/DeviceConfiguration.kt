package com.lagradost.cloudstream4

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import com.lagradost.cloudstream4.generated.resources.Res

internal expect object DeviceConfiguration {
    internal val defaultLayout : DeviceLayout
    internal val isLandscape : Boolean
}

@JvmInline
@Immutable
value class DeviceLayout(private val value: Int) {
    infix fun or(other: DeviceLayout) = DeviceLayout(value or other.value)
    internal fun and(other: DeviceLayout) = (value and other.value) != 0

    companion object {
        val PHONE = DeviceLayout(0b00001)
        val TV = DeviceLayout(0b00010)
        val EMULATOR = DeviceLayout(0b00100)
        val COMPUTER = DeviceLayout(0b01000)

        fun isLandscape(): Boolean = isLayout(TV or EMULATOR) || DeviceConfiguration.isLandscape
        fun isLayout(layoutFlags: DeviceLayout): Boolean = AppPreferences.ui.layout.get().and(layoutFlags)
    }
}


