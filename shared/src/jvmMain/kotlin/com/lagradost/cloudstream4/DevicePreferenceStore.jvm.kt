package com.lagradost.cloudstream4

import androidx.compose.runtime.Composable
import com.mihon.common.preference.InMemoryPreferenceStore

/** As this is mostly for testing so far, we can assume we have a shared global for settings */
internal val settings = AppSettings()
internal val general = GeneralData()
internal val account = AccountData()
internal val db = Database()


@Composable
actual fun rememberAppSettings(): AppSettings {
    return settings
}

fun AppSettings(): AppSettings {
    return AppSettings(preferences = InMemoryPreferenceStore())
}

@Composable
actual fun rememberGeneralData(): GeneralData = general
fun GeneralData(): GeneralData {
    return GeneralData(preferences = InMemoryPreferenceStore())
}

@Composable
actual fun rememberAccountData(): AccountData = account

fun AccountData(): AccountData {
    return AccountData(preferences = InMemoryPreferenceStore())
}

fun Database() = Database(settings = settings, general = general, account = account)

@Composable
actual fun rememberDatabase(): Database = db