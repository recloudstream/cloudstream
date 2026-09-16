package com.lagradost.cloudstream4

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mihon.common.preference.AndroidPreferenceStore


@Composable
actual fun rememberAppSettings() : AppSettings {
    val context = LocalContext.current
    val value = remember(context) { AppSettings(context) }
    return value
}

fun AppSettings(context: Context) : AppSettings {
    return AppSettings(preferences = AndroidPreferenceStore(context))
}
