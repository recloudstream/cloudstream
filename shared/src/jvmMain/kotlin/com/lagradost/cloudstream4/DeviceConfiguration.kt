package com.lagradost.cloudstream4
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.awt.Toolkit

internal actual object DeviceConfiguration {
    internal actual val defaultLayout: DeviceLayout = DeviceLayout.COMPUTER
    internal actual val isLandscape: Boolean = try {
        val size = Toolkit.getDefaultToolkit().screenSize
        size.width > size.height
    } catch (_ : Throwable) {
        false
    }
}