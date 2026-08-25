package com.lagradost.cloudstream3.ui.result.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

fun formatRemainingTime(targetUnixTimeSeconds: Long): String {
    val currentSeconds = System.currentTimeMillis() / 1000
    val diff = targetUnixTimeSeconds - currentSeconds
    if (diff <= 0) return "Releasing soon"
    val days = diff / 86400
    val hours = diff % 86400 / 3600
    val minutes = diff % 3600 / 60
    val seconds = diff % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
fun rememberAiringCountdown(targetUnixTimeSeconds: Long?): String? {
    if (targetUnixTimeSeconds == null || targetUnixTimeSeconds <= 0) return null
    return produceState<String?>(
        initialValue = formatRemainingTime(targetUnixTimeSeconds),
        key1 = targetUnixTimeSeconds
    ) {
        while (true) {
            val currentSeconds = System.currentTimeMillis() / 1000
            val diff = targetUnixTimeSeconds - currentSeconds
            value = formatRemainingTime(targetUnixTimeSeconds)
            if (diff <= 0) break
            if (diff > 86400) {
                delay(60_000L)
            } else {
                delay(1_000L)
            }
        }
    }.value
}
