package com.lagradost.cloudstream3.actions.temp.fcast

import android.util.Log
import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.safefile.closeQuietly
import java.io.DataOutputStream
import java.net.Socket
import kotlin.jvm.Throws

class FcastSession(private val hostAddress: String): AutoCloseable {
    val tag = "FcastSession"

    private var socket: Socket? = null
    @Throws
    @WorkerThread
    fun open(): Socket {
        val socket = Socket(hostAddress, FcastManager.TCP_PORT)
        this.socket = socket
        return socket
    }

    override fun close() {
        socket?.closeQuietly()
        socket = null
    }

    @Throws
    private fun acquireSocket(): Socket {
        return socket ?: open()
    }

    fun ping() {
        sendMessage(Opcode.Ping, null)
    }

    fun <T> sendMessage(opcode: Opcode, message: T) {
        ioSafe {
            val socket = acquireSocket()
            val outputStream = DataOutputStream(socket.getOutputStream())

            val json = message?.toJson()
            val content = json?.toByteArray() ?: ByteArray(0)

            // Little endian starting from 1
            // https://gitlab.com/futo-org/fcast/-/wikis/Protocol-version-1
            val size = content.size + 1

            val sizeArray = ByteArray(4) { num ->
                (size shr 8 * num and 0xff).toByte()
            }

            Log.d(tag, "Sending message with size: $size, opcode: $opcode")
            outputStream.write(sizeArray)
            outputStream.write(ByteArray(1) { opcode.value })
            outputStream.write(content)
        }
    }
}