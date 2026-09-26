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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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