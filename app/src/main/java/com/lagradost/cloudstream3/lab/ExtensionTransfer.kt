package com.lagradost.cloudstream3.lab

import com.lagradost.cloudstream4.viewmodels.DigestPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestException
import java.security.MessageDigest

/** Integrity-checked extension transfer, independent of APK installers and media download queues. */
object ExtensionTransfer {
    suspend fun isFileDigest(file: File, digest: DigestPair?): Boolean = withContext(Dispatchers.IO) {
        if (digest == null || !file.isFile) return@withContext false
        val md = MessageDigest.getInstance(digest.algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                md.update(buffer, 0, count)
            }
        }
        digest.digest.contentEquals(md.digest())
    }

    suspend fun transfer(writeStream: OutputStream, readStream: InputStream, length: Long?,
        downloadProgress: (Long, Long?) -> Unit, digest: DigestPair?) = withContext(Dispatchers.IO) {
        val md = digest?.let { MessageDigest.getInstance(it.algorithm) }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = readStream.read(buffer)
            if (count < 0) break
            writeStream.write(buffer, 0, count)
            md?.update(buffer, 0, count)
            total += count
            downloadProgress(total, length)
        }
        writeStream.flush()
        if (md != null && !md.digest().contentEquals(digest!!.digest)) {
            throw DigestException("Extension digest mismatch")
        }
    }
}
