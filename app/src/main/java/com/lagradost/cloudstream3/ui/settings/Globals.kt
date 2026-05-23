package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import com.lagradost.cloudstream3.DeviceLayout

object Globals {
    var beneneCount = 0

    const val PHONE: Int = DeviceLayout.PHONE
    const val TV: Int = DeviceLayout.TV
    const val EMULATOR: Int = DeviceLayout.EMULATOR

    fun Context.updateTv() {
        DeviceLayout.update()
    }

    fun isLandscape(): Boolean = DeviceLayout.isLandscape()

    fun isLayout(flags: Int): Boolean = DeviceLayout.isLayout(flags)
}
