package com.lagradost.cloudstream4

import com.lagradost.cloudstream4.DeviceLayout.Companion.COMPUTER
import com.lagradost.cloudstream4.DeviceLayout.Companion.EMULATOR
import com.lagradost.cloudstream4.DeviceLayout.Companion.PHONE
import com.lagradost.cloudstream4.DeviceLayout.Companion.TV
import com.mihon.common.preference.PreferenceStore

internal expect object DevicePreferenceStore {
    val store: PreferenceStore
}

object AppPreferences {
    val ui = UiPreferences(DevicePreferenceStore.store)
}

class UiPreferences(store: PreferenceStore) {
    val layout = store.getObjectFromInt(
        key = "app_layout_key",
        defaultValue = DeviceConfiguration.defaultLayout,
        serializer = { value ->
            return@getObjectFromInt when (value) {
                PHONE -> 0
                TV -> 1
                EMULATOR -> 2
                COMPUTER -> 3
                else -> {
                    -1
                }
            }
        },
        deserializer = { value ->
            return@getObjectFromInt when (value) {
                -1 -> DeviceConfiguration.defaultLayout
                0 -> PHONE
                1 -> TV
                2 -> EMULATOR
                3 -> COMPUTER
                else -> DeviceConfiguration.defaultLayout
            }
        }
    )
}