package com.lagradost.cloudstream4

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mihon.common.preference.AndroidAccountPreferenceStore
import com.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.DataPreferenceStore


@Composable
actual fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember(context) { AppSettings(context) }
}

fun AppSettings(context: Context): AppSettings =
    AppSettings(preferences = AndroidPreferenceStore(context))

@Composable
actual fun rememberGeneralData(): GeneralData {
    val context = LocalContext.current
    return remember(context) { GeneralData(context) }
}

fun GeneralData(context: Context): GeneralData =
    GeneralData(preferences = DataPreferenceStore(context))

@Composable
actual fun rememberAccountData(): AccountData {
    val context = LocalContext.current
    return remember(context) { AccountData(context) }
}

fun AccountData(context: Context): AccountData =
    AccountData(preferences = AndroidAccountPreferenceStore(context))

fun Database(context: Context): Database = Database(
    account = AccountData(context),
    settings = AppSettings(context),
    general = GeneralData(context)
)

@Composable
actual fun rememberDatabase(): Database {
    val context = LocalContext.current
    return remember(context) { Database(context) }
}