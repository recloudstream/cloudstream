package com.lagradost.cloudstream3.ui.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import com.fleeksoft.io.OutputStream
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.receivers.PackageInstallerStatusReceiver
import com.lagradost.cloudstream4.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.InputStream

object ApkUpdater : AppUpdater {
    private const val APP_UPDATE_NAME = "CloudStream"
    private const val APP_UPDATE_SUFFIX = "apk"

    @Throws
    override suspend fun update(
        settings: AppSettings,
        url: String,
        downloadProgress: (Long, Long?) -> Unit
    ) {
        val activity = CommonActivity.activity ?: throw ErrorLoadingException("No activity found")
        clearOldFiles(activity)

        val request = app.get(url)
        val length = request.size
        val body = request.body
        body.use { body ->
            val length = length ?: body.contentLength()

            when (settings.updates.apkInstaller.get()) {
                0 -> {
                    packageInstallerDownloader(activity, body, length, downloadProgress)
                }

                else -> {
                    legacyDownloader(activity, body, length, downloadProgress)
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
        body: ResponseBody,
        length: Long?,
        downloadProgress: (Long, Long?) -> Unit
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
            val readStream = body.byteStream()

            // We do not need to buffer this because transfer has large writes
            session.openWrite(activity.packageName, 0, length ?: -1L)
                .use { writeStream ->
                    transfer(writeStream, readStream, length, downloadProgress)
                    session.fsync(writeStream)
                }

            val receiverIntent = Intent(activity, PackageInstallerStatusReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val receiverPendingIntent = PendingIntent.getBroadcast(activity, 0, receiverIntent, flags)

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

    @Throws
    suspend fun transfer(
        writeStream: OutputStream,
        readStream: InputStream,
        length: Long?,
        downloadProgress: (Long, Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read: Int
        var transferred: Long = 0
        while ((readStream.read(buffer, 0, DEFAULT_BUFFER_SIZE)
                .also { read = it }) >= 0
        ) {
            writeStream.write(buffer, 0, read)
            transferred += read.toLong()
            downloadProgress(transferred, length)
            context.ensureActive()
        }
        writeStream.flush()
    }

    @Throws
    suspend fun legacyDownloader(
        activity: Activity,
        body: ResponseBody,
        length: Long?,
        downloadProgress: (Long, Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadedFile = File.createTempFile(APP_UPDATE_NAME, ".$APP_UPDATE_SUFFIX")
        val readStream = body.byteStream()

        // We do not need to buffer this because transfer has large writes
        downloadedFile.outputStream().use { writeStream ->
            transfer(writeStream, readStream, length, downloadProgress)
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