package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

inline fun <reified T> Intent.getSafeParcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        @Suppress("DEPRECATION")
        getParcelableExtra(key) else getParcelableExtra(key, T::class.java)

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerBroadcastReceiver(receiver: BroadcastReceiver, actionFilter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Register receiver with the context with flag to indicate internal usage
        registerReceiver(receiver, actionFilter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        // For older versions, no special export flag is needed
        registerReceiver(receiver, actionFilter)
    }
}