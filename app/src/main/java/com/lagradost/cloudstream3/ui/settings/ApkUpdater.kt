package com.lagradost.cloudstream3.ui.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.receivers.PackageInstallerStatusReceiver
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.viewmodels.AppUpdater
import com.lagradost.cloudstream4.viewmodels.DigestPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestException
import java.security.MessageDigest

object ApkUpdater : AppUpdater {
    private const val APP_UPDATE_NAME = "CloudStream"
    private const val APP_UPDATE_SUFFIX = "apk"

    @Throws
    override suspend fun update(
        settings: AppSettings,
        url: String,
        digest: DigestPair?,
        downloadProgress: (Long, Long?) -> Unit
    ) {
        val activity = CommonActivity.activity ?: throw ErrorLoadingException("No activity found")

        // Always install from the digest keyed cache file, never straight
        // from the raw download stream. If the correct apk is already cached
        // (e.g. pre-downloaded) it is reused as is, otherwise it is downloaded
        // into the cache first. As the cache is keyed by digest, a stale apk
        // from an older build under the same floating tag (e.g. "pre-release")
        // can never be installed here.
        if (digest != null) {
            val cached = downloadToCache(activity, url, digest)
            cached.inputStream().use { readStream ->
                when (settings.updates.apkInstaller.get()) {
                    0 -> packageInstallerDownloader(
                        activity,
                        readStream,
                        cached.length(),
                        digest,
                        downloadProgress
                    )

                    else -> legacyDownloader(
                        activity,
                        readStream,
                        cached.length(),
                        digest,
                        downloadProgress
                    )
                }
            }
            return
        }

        clearOldFiles(activity)

        val request = app.get(url)
        val length = request.size
        val body = request.body
        body.use { body ->
            val length = length ?: body.contentLength()
            val readStream = body.byteStream()

            when (settings.updates.apkInstaller.get()) {
                0 -> {
                    packageInstallerDownloader(
                        activity,
                        readStream,
                        length,
                        digest,
                        downloadProgress
                    )
                }

                else -> {
                    legacyDownloader(activity, readStream, length, digest, downloadProgress)
                }
            }
        }
    }

    fun clearOldFiles(activity: Activity) {
        // Delete old files
        activity.cacheDir.listFiles()?.filter {
            it.name.startsWith(APP_UPDATE_NAME) && it.extension == APP_UPDATE_SUFFIX
        }?.forEach {
            deleteFileOnExit(it)
        }
    }

    /**
     * The cached update apk of a specific release, named by its digest so
     * different builds never share a cache file, even under a floating tag.
     */
    private fun getCachedUpdateFile(context: Context, digest: DigestPair): File {
        return File(
            context.cacheDir,
            "${APP_UPDATE_NAME}_${digest.algorithm.lowercase()}_${digest.digest.toHexString()}.$APP_UPDATE_SUFFIX"
        )
    }

    /**
     * Downloads the apk of a release into its digest keyed cache file and
     * returns that file. If the correct apk is already cached, it is returned
     * without downloading anything.
     */
    @Throws
    private suspend fun downloadToCache(
        activity: Activity,
        url: String,
        digest: DigestPair
    ): File {
        val target = getCachedUpdateFile(activity, digest)
        if (target.exists()) {
            if (isFileDigest(target, digest)) {
                return target
            }

            // The cached file belongs to another build, delete it and
            // download this release instead of failing.
            target.delete()
        }

        // Drop cached apks of other builds before downloading this one
        clearOldFiles(activity)

        val request = app.get(url)
        val body = request.body
        val tempFile =
            File.createTempFile(APP_UPDATE_NAME, ".$APP_UPDATE_SUFFIX", activity.cacheDir)
        try {
            body.use { responseBody ->
                tempFile.outputStream().use { writeStream ->
                    transfer(
                        writeStream,
                        responseBody.byteStream(),
                        request.size ?: responseBody.contentLength(),
                        { _, _ -> },
                        digest
                    )
                }
            }
            moveIntoPlace(tempFile, target)
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
        return target
    }

    override suspend fun cacheUpdate(
        url: String,
        digest: DigestPair?
    ): String? = withContext(Dispatchers.IO) {
        // Without a digest we cannot key or verify the cache, so do nothing.
        if (digest == null) return@withContext null
        val activity = CommonActivity.activity ?: return@withContext null
        try {
            downloadToCache(activity, url, digest).absolutePath
        } catch (_: Throwable) {
            // Pre-downloading is best effort only, it must never fail the
            // update check, the apk is simply downloaded again on update.
            null
        }
    }

    /**
     * Moves [temp] to [target], preferring an atomic move. If the filesystem
     * does not support atomic moves, the old file is kept as a backup until
     * the new one is in place, and restored if the move fails.
     */
    @Throws
    private fun moveIntoPlace(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            return
        } catch (_: AtomicMoveNotSupportedException) {
            // Fall through to the backup based move below
        }

        val backup = File(target.parentFile, target.name + ".bak")
        backup.delete()
        val hadTarget = target.exists() && target.renameTo(backup)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (t: Throwable) {
            if (hadTarget) {
                target.delete()
                backup.renameTo(target)
            }
            throw t
        }
        if (hadTarget) {
            backup.delete()
        }
    }

    /** https://medium.com/@solrudev/painless-building-of-an-android-package-installer-app-d5a09b5df432 */
    @SuppressLint("RequestInstallPackagesPolicy")
    @Throws
    suspend fun packageInstallerDownloader(
        activity: Activity,
        readStream: InputStream,
        length: Long?,
        digest: DigestPair?,
        downloadProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var sessionId: Int? = null
        val packageInstaller = activity.packageManager.packageInstaller
        try {
            val installParams =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (length != null) {
                installParams.setSize(length)
            }

            sessionId = packageInstaller.createSession(installParams)
            val session = packageInstaller.openSession(sessionId)

            // We do not need to buffer this because transfer has large writes
            session.openWrite(activity.packageName, 0, length ?: -1L)
                .use { writeStream ->
                    transfer(writeStream, readStream, length, downloadProgress, digest)
                    session.fsync(writeStream)
                }

            val receiverIntent = Intent(activity, PackageInstallerStatusReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val receiverPendingIntent =
                PendingIntent.getBroadcast(activity, 0, receiverIntent, flags)

            // Avoid delayed updates, and just commit instantly
            session.commit(receiverPendingIntent.intentSender)
            session.close()
        } catch (t: Throwable) {
            sessionId?.let { sessionId ->
                packageInstaller.abandonSession(sessionId)
            }
            throw t
        }
    }

    /**
     * Write the "readStream" to the "writeStream", while notifying the "downloadProgress" and
     * calculating the digest
     *
     * ------------------------------------------------------------------------------------------
     *
     * throws a DigestException if the Digest can be calculated (non-null + correct algorithm),
     * and is mismatched
     *
     * throws a CancellationException if canceled, but may not be "instant" if the read is blocking
     *
     * throws IOException and similar for reading
     *
     * ------------------------------------------------------------------------------------------
     *
     * In case of crashes from the digest, we ignore it as we do not want to "block" someone from
     * updating if they got a broken OS without the desired algorithm or implementation.
     *
     * This is because a malicious CDN should not be able to "crash" the algorithm, but a broken
     * OS will. And recovering from a broken OS by ignoring it is better than refusing it.
     * */
    @Throws
    suspend fun transfer(
        writeStream: OutputStream,
        readStream: InputStream,
        length: Long?,
        downloadProgress: (Long, Long?) -> Unit,
        digest: DigestPair?,
    ) = withContext(Dispatchers.IO) {
        val md = digest?.algorithm?.let { digestAlgorithm ->
            safe {
                MessageDigest.getInstance(digestAlgorithm)
            }
        }

        val context = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read: Int
        var transferred: Long = 0
        while ((readStream.read(buffer, 0, DEFAULT_BUFFER_SIZE)
                .also { read = it }) >= 0
        ) {
            writeStream.write(buffer, 0, read)

            safe {
                md?.update(buffer, 0, read)
            }

            transferred += read.toLong()
            downloadProgress(transferred, length)
            context.ensureActive()
        }
        writeStream.flush()

        val check = safe { md?.digest() }
        if (check != null && !check.contentEquals(digest?.digest)) {
            throw DigestException("Mismatched digest on transfer ${digest?.algorithm} : ${check.toHexString()} / ${digest?.digest?.toHexString()}")
        }
    }

    /**
     * Returns true if we have a non-null digest that matches a digest on the specified existing file
     * */
    suspend fun isFileDigest(file: File, digest: DigestPair?): Boolean {
        if (digest == null) {
            return false
        }
        try {
            val fileDigest = calculateDigest(
                digestAlgorithm = digest.algorithm,
                readStream = file.inputStream()
            )
            return fileDigest != null && digest.digest.contentEquals(fileDigest)
        } catch (_: Throwable) {
            // File not found/security/read issues, not very useful errors
        }
        return false
    }

    /**
     * Calculates the digest of a stream with the specified algorithm, use transfer instead if
     * you already download something.
     *
     * Will throw read errors
     * Will return null if the algorithm is null or does not exist/has some error
     * */
    @Throws
    suspend fun calculateDigest(digestAlgorithm: String?, readStream: InputStream) =
        withContext(Dispatchers.IO) {
            if (digestAlgorithm == null) return@withContext null

            val md =
                safe {
                    MessageDigest.getInstance(digestAlgorithm)
                } ?: return@withContext null

            val context = currentCoroutineContext()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (readStream.read(buffer, 0, DEFAULT_BUFFER_SIZE)
                    .also { read = it } >= 0
            ) {
                try {
                    md.update(buffer, 0, read)
                } catch (_: Throwable) {
                    return@withContext null
                }
                context.ensureActive()
            }
            return@withContext safe { md.digest() }
        }

    @Throws
    suspend fun legacyDownloader(
        activity: Activity,
        readStream: InputStream,
        length: Long?,
        digest: DigestPair?,
        downloadProgress: (Long, Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadedFile = File.createTempFile(APP_UPDATE_NAME, ".$APP_UPDATE_SUFFIX")

        // We do not need to buffer this because transfer has large writes
        downloadedFile.outputStream().use { writeStream ->
            transfer(writeStream, readStream, length, downloadProgress, digest)
        }

        openApk(activity, downloadedFile)
    }

    fun openApk(context: Context, file: File) {
        val contentUri = FileProvider.getUriForFile(
            context, BuildConfig.APPLICATION_ID + ".provider", file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            data = contentUri
        }
        context.startActivity(installIntent)
    }
}