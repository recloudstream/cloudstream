package com.lagradost.cloudstream3.utils.storage

import android.net.Uri
import com.hippo.unifile.UniFile
import com.hippo.unifile.UniRandomAccessFile
import com.lagradost.cloudstream3.mvvm.logError
import okhttp3.internal.closeQuietly
import java.io.InputStream
import java.io.OutputStream

private fun UniFile.toFile(): SafeFile {
    return UniFileWrapper(this)
}

fun <T> safe(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        null
    }
}

class UniFileWrapper(val file: UniFile) : SafeFile {
    override fun createFile(displayName: String?): SafeFile? {
        return file.createFile(displayName)?.toFile()
    }

    override fun createDirectory(directoryName: String?): SafeFile? {
        return file.createDirectory(directoryName)?.toFile()
    }

    override fun uri(): Uri? {
        return safe { file.uri }
    }

    override fun name(): String? {
        return safe { file.name }
    }

    override fun type(): String? {
        return safe { file.type }
    }

    override fun filePath(): String? {
        return safe { file.filePath }
    }

    override fun isDirectory(): Boolean? {
        return safe { file.isDirectory }
    }

    override fun isFile(): Boolean? {
        return safe { file.isFile }
    }

    override fun lastModified(): Long? {
        return safe { file.lastModified() }
    }

    override fun length(): Long? {
        return safe {
            val len = file.length()
            if (len <= 1) {
                val inputStream = this.openInputStream() ?: return@safe null
                try {
                    inputStream.available().toLong()
                } finally {
                    inputStream.closeQuietly()
                }
            } else {
                len
            }
        }
    }

    override fun canRead(): Boolean {
        return safe { file.canRead() } ?: false
    }

    override fun canWrite(): Boolean {
        return safe { file.canWrite() } ?: false
    }

    override fun delete(): Boolean {
        return safe { file.delete() } ?: false
    }

    override fun exists(): Boolean? {
        return safe { file.exists() }
    }

    override fun listFiles(): List<SafeFile>? {
        return safe { file.listFiles()?.mapNotNull { it?.toFile() } }
    }

    override fun findFile(displayName: String?, ignoreCase: Boolean): SafeFile? {
        return safe { file.findFile(displayName, ignoreCase)?.toFile() }
    }

    override fun renameTo(name: String?): Boolean {
        return safe { file.renameTo(name) } ?: return false
    }

    override fun openOutputStream(append: Boolean): OutputStream? {
        return safe { file.openOutputStream(append) }
    }

    override fun openInputStream(): InputStream? {
        return safe { file.openInputStream() }
    }

    override fun createRandomAccessFile(mode: String?): UniRandomAccessFile? {
        return safe { file.createRandomAccessFile(mode) }
    }
}