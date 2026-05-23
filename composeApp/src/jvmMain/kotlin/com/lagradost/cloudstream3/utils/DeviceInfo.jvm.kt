package com.lagradost.cloudstream3

import java.awt.Toolkit

internal actual object DeviceInfo {
    actual fun getDeviceType(): DeviceType = DeviceType.COMPUTER

    actual fun isLandscape(): Boolean {
        return try {
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            screenSize.width > screenSize.height
        } catch (_: Exception) {
            true // Assume landscape as that is more likely on JVM
        }
    }

    actual fun getLayoutPreference(): Int = -1
}
