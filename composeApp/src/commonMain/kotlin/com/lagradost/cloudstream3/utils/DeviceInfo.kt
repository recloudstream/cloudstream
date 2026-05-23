package com.lagradost.cloudstream3

internal expect object DeviceInfo {
    fun getDeviceType(): DeviceType
    fun isLandscape(): Boolean
    fun getLayoutPreference(): Int
}

enum class DeviceType {
    PHONE, TV, EMULATOR, COMPUTER
}
