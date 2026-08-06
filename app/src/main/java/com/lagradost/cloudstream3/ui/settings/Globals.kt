package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import com.lagradost.cloudstream4.utils.DeviceLayout

// TODO: remove and use DeviceLayout directly.
// this currently just acts as a wrapper to
// avoid changing so much at once.
object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b00001
    const val TV: Int = 0b00010
    const val EMULATOR: Int = 0b00100

    fun Context.updateTv() {
        DeviceLayout.update()
    }

    fun isLandscape(): Boolean = DeviceLayout.isLandscape()

    fun isLayout(flags: Int): Boolean = DeviceLayout.isLayout(DeviceLayout.Layout(flags))
}
