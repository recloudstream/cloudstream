package com.lagradost.cloudstream4

import com.mihon.common.preference.InMemoryPreferenceStore
import com.mihon.common.preference.PreferenceStore

internal actual object DevicePreferenceStore {
    actual val store: PreferenceStore = InMemoryPreferenceStore()
}