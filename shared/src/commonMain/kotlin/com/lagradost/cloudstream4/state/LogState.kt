package com.lagradost.cloudstream4.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class LogLevel(val identifier: String, val color : Color) {
    Fatal("WTF", Color.Magenta),
    Error("E", Color.Red),
    Warning("W", Color.Yellow),
    Info("I", Color.White),
    Debug("D", Color.Green),
    Verbose("V", Color.Gray);
    fun highestLogLevel(other : LogLevel) = LogLevel.entries[minOf(ordinal, other.ordinal)]
    fun lowestLogLevel(other : LogLevel) = LogLevel.entries[maxOf(ordinal, other.ordinal)]
}

@Immutable
data class LogItem(
    val date: Instant,
    val level: LogLevel,
    val tag: String,
    val message: String,
    override val uuid: Uuid = Uuid.random(),
) : UniqueItem

interface Log {
    /** Fatal */
    fun f(tag: String, message: String) =
        log(level = LogLevel.Fatal, tag = tag, message = message)
    /** Warning */
    fun w(tag: String, message: String) =
        log(level = LogLevel.Warning, tag = tag, message = message)
    /** Info */
    fun i(tag: String, message: String) =
        log(level = LogLevel.Info, tag = tag, message = message)
    /** Verbose */
    fun v(tag: String, message: String) =
        log(level = LogLevel.Verbose, tag = tag, message = message)
    /** Error */
    fun e(tag: String, message: String) =
        log(level = LogLevel.Error, tag = tag, message = message)
    /** Debug */
    fun d(tag: String, message: String) =
        log(level = LogLevel.Debug, tag = tag, message = message)

    private fun log(level: LogLevel, tag: String, message: String) {
        log(LogItem(date = Clock.System.now(), level = level, tag = tag, message = message))
    }

    fun log(item: LogItem)
}