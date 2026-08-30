package com.lagradost.cloudstream4

import android.content.Context
import com.lagradost.api.getContext
import com.lagradost.cloudstream4.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.PreferenceStore

internal actual object DevicePreferenceStore {
    // Let's hope this does not crash, no idea about the initialization order
    actual val store: PreferenceStore = AndroidPreferenceStore(getContext() as Context)
}