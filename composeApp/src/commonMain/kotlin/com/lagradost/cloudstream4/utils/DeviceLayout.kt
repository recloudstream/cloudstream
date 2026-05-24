package com.lagradost.cloudstream4.utils

object DeviceLayout {
    const val PHONE: Int = 0b00001
    const val TV: Int = 0b00010
    const val EMULATOR: Int = 0b00100
    const val COMPUTER: Int = 0b01000

    private var layoutId = -1
    // TODO when fully on Compose
    // private val layoutId: Int get() = resolveLayout()

    /**
     * Returns true if the layout is any of the flags, so
     * so isLayout(TV or EMULATOR) is a valid statement 
     * for checking if the layout is in the emulator
     * or tv. Auto will become the "TV" or the
     * "PHONE" layout.
     *
     * Valid flags are: PHONE, TV, EMULATOR, or COMPUTER
     */
    fun isLayout(flags: Int): Boolean = (layoutId and flags) != 0

    /** Returns true if the current orientation is landscape. */
    fun isLandscape(): Boolean =
        isLayout(TV or EMULATOR) || DeviceInfo.isLandscape()

    /**
     * Updates the cached layout ID from preferences and device detection.
     *
     * TODO: Remove caching once fully migrated to Compose, layout will be read
     * directly via [DeviceInfo] during composition where caching is handled by
     * the Compose runtime.
     */
    fun update() {
        layoutId = resolveLayout()
    }

    private fun resolveLayout(): Int {
        return when (DeviceInfo.getLayoutPreference()) {
            -1 -> when (DeviceInfo.getDeviceType()) {
                DeviceType.COMPUTER -> COMPUTER
                DeviceType.EMULATOR -> EMULATOR
                DeviceType.PHONE -> PHONE
                DeviceType.TV -> TV
            }
            0 -> PHONE
            1 -> TV
            2 -> EMULATOR
            else -> PHONE
        }
    }
}
