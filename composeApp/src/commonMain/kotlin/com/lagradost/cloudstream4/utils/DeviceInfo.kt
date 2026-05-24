package com.lagradost.cloudstream4.utils

internal expect object DeviceInfo {
    fun getDeviceType(): DeviceType
    fun isLandscape(): Boolean
    fun getLayoutPreference(): Int
}

enum class DeviceType {
    PHONE, TV, EMULATOR, COMPUTER
}
