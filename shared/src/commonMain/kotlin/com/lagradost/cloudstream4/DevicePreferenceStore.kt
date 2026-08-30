package com.lagradost.cloudstream4

import com.mihon.common.preference.PreferenceStore

internal expect object DevicePreferenceStore {
    val store: PreferenceStore
}

object AppPreferences {
    val ui = UiPreferences(DevicePreferenceStore.store)
}

class UiPreferences(store: PreferenceStore) {
}