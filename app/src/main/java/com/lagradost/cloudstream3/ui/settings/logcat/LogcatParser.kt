package com.lagradost.cloudstream3.ui.settings.logcat

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import kotlin.time.Instant
import kotlinx.io.Buffer
import kotlinx.io.asSource
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readString
import kotlinx.io.readUShortLe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun Instant.toHumanReadable(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    return formatter.format(Date(this.toEpochMilliseconds()))
}
@Immutable
data class LogcatItem(
    val date: Instant,
    val pid: Int,
    val tid: Int,
    val level: LogcatLevel?,
    val tag: String,
    val message: String,
) {
    override fun toString(): String {
        return "${date.toHumanReadable()} $pid-$tid $tag ${level?.identifier ?: "?"} $message"
    }
}

enum class LogcatLevel(val identifier: String) {
    Fatal("WTF"),
    Error("E"),
    Warning("W"),
    Info("I"),
    Debug("D"),
    Verbose("V"),
}

/**https://github.com/brudaswen/android-logcat/blob/main/library/logcat-core/src/main/kotlin/de/brudaswen/android/logcat/core/parser/LogcatBinaryParser.kt  */
class LogcatBinaryParser(
    private val input: InputStream,
) : Closeable by input {
    val source = input.asSource()
    private val buffer = Buffer()

    /**
     * Parse one [LogcatItem] from the current [input] stream.
     *
     * @return The parsed [LogcatItem] or `null` if stream reached EOF.
     */
    suspend fun parseItem(): LogcatItem? = withContext(Dispatchers.IO) {
        val firstByte = input.read()
        if (firstByte == -1) return@withContext null

        // Read v1 header
        buffer.writeByte(firstByte.toByte())
        buffer.write(source = source, byteCount = 19)

        val len = buffer.readUShortLe().toInt()
        val headerSize = buffer.readUShortLe().toInt()
        val pid = buffer.readIntLe()
        val tid = buffer.readIntLe()
        val sec = buffer.readIntLe()
        val nsec = buffer.readIntLe()

        // Read additional header fields
        buffer.write(source = source, byteCount = headerSize - 20L)

        val additionalHeaderBytes = (headerSize - 20).coerceAtLeast(0)
        buffer.readByteArray(byteCount = additionalHeaderBytes)

        // Read payload
        buffer.write(source = source, byteCount = len.toLong())

        val priority = buffer.readByte()

        val payload = buffer.readString()
        val texts = payload.split('\u0000', limit = 2)
        val tag = texts.getOrNull(0).orEmpty()
        val message = texts.getOrNull(1).orEmpty().removeSuffix("\u0000").trim()

        // Clear buffer
        buffer.clear()

        // Convert raw values to item
        LogcatItem(
            sec = sec,
            nsec = nsec,
            priority = priority,
            pid = pid,
            tid = tid,
            tag = tag,
            message = message,
        )
    }

    private fun LogcatItem(
        sec: Int,
        nsec: Int,
        priority: Byte,
        pid: Int,
        tid: Int,
        tag: String,
        message: String,
    ): LogcatItem {
        val date = Instant.fromEpochSeconds(
            epochSeconds = sec.toLong(),
            nanosecondAdjustment = nsec,
        )

        val level = when (priority) {
            2.toByte() -> LogcatLevel.Verbose
            3.toByte() -> LogcatLevel.Debug
            4.toByte() -> LogcatLevel.Info
            5.toByte() -> LogcatLevel.Warning
            6.toByte() -> LogcatLevel.Error
            7.toByte() -> LogcatLevel.Fatal
            else -> null
        }

        return LogcatItem(
            date = date,
            pid = pid,
            tid = tid,
            level = level,
            tag = tag,
            message = message,
        )
    }
}