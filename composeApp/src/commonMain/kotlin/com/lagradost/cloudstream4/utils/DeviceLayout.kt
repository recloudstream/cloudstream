package com.lagradost.cloudstream4.utils

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.lagradost.cloudstream4.preferences.PreferenceDefaults
import kotlin.jvm.JvmInline

object DeviceLayout {
    @Immutable
    @JvmInline // This still works but has no affect on non-JVM targets
    value class Layout(private val value: Int) {
        infix fun or(other: Layout) = Layout(value or other.value)
        internal fun and(other: Layout) = (value and other.value) != 0
    }

    val PHONE = Layout(0b00001)
    val TV = Layout(0b00010)
    val EMULATOR = Layout(0b00100)
    val COMPUTER = Layout(0b01000)

    private var layoutId = Layout(-1)
    private val layoutState = mutableStateOf(layoutId)
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
    fun isLayout(flags: Layout): Boolean = layoutId.and(flags)

    /**
     * Returns a [State] that is true if the layout is any of the flags, so
     * so isLayoutState(TV or EMULATOR) is a valid statement
     * for checking if the layout is in the emulator
     * or tv. Auto will become the "TV" or the
     * "PHONE" layout.
     *
     * Valid flags are: PHONE, TV, EMULATOR, or COMPUTER
     */
    fun isLayoutState(layoutFlags: Layout): State<Boolean> =
        derivedStateOf { layoutState.value.and(layoutFlags) }

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
        layoutState.value = layoutId
    }

    private fun resolveLayout(): Layout {
        return when (DeviceInfo.getLayoutPreference()) {
            PreferenceDefaults.APP_LAYOUT -> DeviceInfo.getDetectedLayout()
            0 -> PHONE
            1 -> TV
            2 -> EMULATOR
            else -> PHONE
        }
    }
}
