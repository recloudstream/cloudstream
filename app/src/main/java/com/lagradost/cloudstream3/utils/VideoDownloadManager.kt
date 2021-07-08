package com.lagradost.cloudstream3.utils

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.services.RESTART_NONE
import com.lagradost.cloudstream3.services.START_VALUE_KEY
import com.lagradost.cloudstream3.services.VideoDownloadKeepAliveService
import com.lagradost.cloudstream3.services.VideoDownloadService
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Thread.sleep
import java.net.URL
import java.net.URLConnection
import java.util.*


const val DOWNLOAD_CHANNEL_ID = "cloudstream3.general"
const val DOWNLOAD_CHANNEL_NAME = "Downloads"
const val DOWNLOAD_CHANNEL_DESCRIPT = "The download notification channel"

object VideoDownloadManager {
    var maxConcurrentDownloads = 3
    private var currentDownloads = mutableListOf<Int>()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    @DrawableRes
    const val imgDone = R.drawable.rddone

    @DrawableRes
    const val imgDownloading = R.drawable.rdload

    @DrawableRes
    const val imgPaused = R.drawable.rdpause

    @DrawableRes
    const val imgStopped = R.drawable.rderror

    @DrawableRes
    const val imgError = R.drawable.rderror

    @DrawableRes
    const val pressToPauseIcon = R.drawable.ic_baseline_pause_24

    @DrawableRes
    const val pressToResumeIcon = R.drawable.ic_baseline_play_arrow_24

    @DrawableRes
    const val pressToStopIcon = R.drawable.exo_icon_stop

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    data class DownloadEpisodeMetadata(
        val id: Int,
        val mainName: String,
        val sourceApiName: String?,
        val poster: String?,
        val name: String?,
        val season: Int?,
        val episode: Int?
    )

    data class DownloadItem(
        val source: String?,
        val folder: String?,
        val ep: DownloadEpisodeMetadata,
        val links: List<ExtractorLink>
    )

    data class DownloadResumePackage(
        val item: DownloadItem,
        val linkIndex: Int?,
    )

    data class DownloadedFileInfo(
        val totalBytes: Long,
        val relativePath: String,
        val displayName: String,
    )

    data class DownloadedFileInfoResult(
        val fileLength: Long,
        val totalBytes: Long,
        val path: Uri,
    )

    data class DownloadQueueResumePackage(
        val index: Int,
        val pkg: DownloadResumePackage,
    )

    private const val SUCCESS_DOWNLOAD_DONE = 1
    private const val SUCCESS_STOPPED = 2
    private const val ERROR_DELETING_FILE = -1
    private const val ERROR_CREATE_FILE = -2
    private const val ERROR_OPEN_FILE = -3
    private const val ERROR_TOO_SMALL_CONNECTION = -4
    private const val ERROR_WRONG_CONTENT = -5
    private const val ERROR_CONNECTION_ERROR = -6
    private const val ERROR_MEDIA_STORE_URI_CANT_BE_CREATED = -7
    private const val ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM = -8
    private const val ERROR_CONTENT_RESOLVER_NOT_FOUND = -9

    const val KEY_RESUME_PACKAGES = "download_resume"
    const val KEY_DOWNLOAD_INFO = "download_info"
    const val KEY_RESUME_QUEUE_PACKAGES = "download_q_resume"

    val downloadStatus = HashMap<Int, DownloadType>()
    val downloadStatusEvent = Event<Pair<Int, DownloadType>>()
    val downloadEvent = Event<Pair<Int, DownloadActionType>>()
    val downloadProgressEvent = Event<Pair<Int, Long>>()
    private val downloadQueue = LinkedList<DownloadResumePackage>()

    private var hasCreatedNotChanel = false
    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = DOWNLOAD_CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = DOWNLOAD_CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    private fun Context.getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(this)
            .asBitmap()
            .load(url).into(720, 720)
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    private fun createNotification(
        context: Context,
        source: String?,
        linkName: String?,
        ep: DownloadEpisodeMetadata,
        state: DownloadType,
        progress: Long,
        total: Long,
    ) {
        main { // DON'T WANT TO SLOW IT DOWN
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentTitle(ep.mainName)
                .setSmallIcon(
                    when (state) {
                        DownloadType.IsDone -> imgDone
                        DownloadType.IsDownloading -> imgDownloading
                        DownloadType.IsPaused -> imgPaused
                        DownloadType.IsFailed -> imgError
                        DownloadType.IsStopped -> imgStopped
                    }
                )

            if (ep.sourceApiName != null) {
                builder.setSubText(ep.sourceApiName)
            }

            if (source != null) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    data = source.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
                builder.setContentIntent(pendingIntent)
            }

            if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                builder.setProgress(total.toInt(), progress.toInt(), false)
            }

            val rowTwoExtra = if (ep.name != null) " - ${ep.name}\n" else ""
            val rowTwo = if (ep.season != null && ep.episode != null) {
                "S${ep.season}:E${ep.episode}" + rowTwoExtra
            } else if (ep.episode != null) {
                "Episode ${ep.episode}" + rowTwoExtra
            } else {
                (ep.name ?: "") + ""
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ep.poster != null) {
                    val poster = withContext(Dispatchers.IO) {
                        context.getImageBitmapFromUrl(ep.poster)
                    }
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }

                val progressPercentage = progress * 100 / total
                val progressMbString = "%.1f".format(progress / 1000000f)
                val totalMbString = "%.1f".format(total / 1000000f)

                val bigText =
                    if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                        (if (linkName == null) "" else "$linkName\n") + "$rowTwo\n$progressPercentage % ($progressMbString MB/$totalMbString MB)"
                    } else if (state == DownloadType.IsFailed) {
                        "Download Failed - $rowTwo"
                    } else if (state == DownloadType.IsDone) {
                        "Download Done - $rowTwo"
                    } else {
                        "Download Canceled - $rowTwo"
                    }

                val bodyStyle = NotificationCompat.BigTextStyle()
                bodyStyle.bigText(bigText)
                builder.setStyle(bodyStyle)
            } else {
                val txt = if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                    rowTwo
                } else if (state == DownloadType.IsFailed) {
                    "Download Failed - $rowTwo"
                } else if (state == DownloadType.IsDone) {
                    "Download Done - $rowTwo"
                } else {
                    "Download Canceled - $rowTwo"
                }

                builder.setContentText(txt)
            }

            if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<DownloadActionType> = ArrayList()
                // INIT
                if (state == DownloadType.IsDownloading) {
                    actionTypes.add(DownloadActionType.Pause)
                    actionTypes.add(DownloadActionType.Stop)
                }

                if (state == DownloadType.IsPaused) {
                    actionTypes.add(DownloadActionType.Resume)
                    actionTypes.add(DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val actionResultIntent = Intent(context, VideoDownloadService::class.java)

                    actionResultIntent.putExtra(
                        "type", when (i) {
                            DownloadActionType.Resume -> "resume"
                            DownloadActionType.Pause -> "pause"
                            DownloadActionType.Stop -> "stop"
                        }
                    )

                    actionResultIntent.putExtra("id", ep.id)

                    val pending: PendingIntent = PendingIntent.getService(
                        // BECAUSE episodes lying near will have the same id +1, index will give the same requested as the previous episode, *100000 fixes this
                        context, (4337 + index * 100000 + ep.id),
                        actionResultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                DownloadActionType.Resume -> pressToResumeIcon
                                DownloadActionType.Pause -> pressToPauseIcon
                                DownloadActionType.Stop -> pressToStopIcon
                            }, when (i) {
                                DownloadActionType.Resume -> "Resume"
                                DownloadActionType.Pause -> "Pause"
                                DownloadActionType.Stop -> "Cancel"
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(ep.id, builder.build())
            }
        }
    }

    private const val reservedChars = "|\\?*<\":>+[]/\'"
    fun sanitizeFilename(name: String): String {
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        return tempName.replace("  ", " ").trim(' ')
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.getExistingDownloadUriOrNullQ(relativePath: String, displayName: String): Uri? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            //MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
            //MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
        )

        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath' AND " + "${MediaStore.MediaColumns.DISPLAY_NAME}='$displayName'"

        val result = this.query(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            projection, selection, null, null
        )

        result.use { c ->
            if (c != null && c.count >= 1) {
                c.moveToFirst().let {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    /*
                    val cDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val cRelativePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))*/

                    return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                    )
                }
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun ContentResolver.getFileLength(fileUri: Uri): Long {
        return this.openFileDescriptor(fileUri, "r")
            .use { it?.statSize ?: 0 }
    }

    private fun isScopedStorage(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun downloadSingleEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        link: ExtractorLink,
        tryResume: Boolean = false,
    ): Int {
        val name = sanitizeFilename(ep.name ?: "Episode ${ep.episode}")

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + '/' + folder + '/').replace('/', File.separatorChar)
        val displayName = "$name.mp4"

        val normalPath = "${Environment.getExternalStorageDirectory()}${File.separatorChar}$relativePath$displayName"
        var resume = tryResume

        val fileStream: OutputStream
        val fileLength: Long

        fun deleteFile(): Int {
            if (isScopedStorage()) {
                val lastContent = context.contentResolver.getExistingDownloadUriOrNullQ(relativePath, displayName)
                if (lastContent != null) {
                    context.contentResolver.delete(lastContent, null, null)
                }
            } else {
                if (!File(normalPath).delete()) return ERROR_DELETING_FILE
            }
            return SUCCESS_STOPPED
        }

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return ERROR_CONTENT_RESOLVER_NOT_FOUND

            val currentExistingFile =
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) // CURRENT FILE WITH THE SAME PATH

            fileLength =
                if (currentExistingFile == null || !resume) 0 else cr.getFileLength(currentExistingFile) // IF NOT RESUME THEN 0, OTHERWISE THE CURRENT FILE SIZE

            if (!resume && currentExistingFile != null) { // DELETE FILE IF FILE EXITS AND NOT RESUME
                val rowsDeleted = context.contentResolver.delete(currentExistingFile, null, null)
                if (rowsDeleted < 1) {
                    println("ERROR DELETING FILE!!!")
                }
            }

            val newFileUri = if (resume && currentExistingFile != null) currentExistingFile else {
                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI

                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        relativePath
                    )
                }

                cr.insert(
                    contentUri,
                    newFile
                ) ?: return ERROR_MEDIA_STORE_URI_CANT_BE_CREATED
            }

            fileStream = cr.openOutputStream(newFileUri, "w")
                ?: return ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM
        } else {
            // NORMAL NON SCOPED STORAGE FILE CREATION
            val rFile = File(normalPath)
            if (!rFile.exists()) {
                fileLength = 0
                rFile.parentFile?.mkdirs()
                if (!rFile.createNewFile()) return ERROR_CREATE_FILE
            } else {
                if (resume) {
                    fileLength = rFile.length()
                } else {
                    fileLength = 0
                    rFile.parentFile?.mkdirs()
                    if (!rFile.delete()) return ERROR_DELETING_FILE
                    if (!rFile.createNewFile()) return ERROR_CREATE_FILE
                }
            }
            fileStream = FileOutputStream(rFile, false)
        }
        if (fileLength == 0L) resume = false

        // CONNECT
        val connection: URLConnection = URL(link.url.replace(" ", "%20")).openConnection() // IDK OLD PHONES BE WACK

        // SET CONNECTION SETTINGS
        connection.connectTimeout = 10000
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (link.referer.isNotEmpty()) connection.setRequestProperty("Referer", link.referer)
        if (resume) connection.setRequestProperty("Range", "bytes=${fileLength}-")
        val resumeLength = (if (resume) fileLength else 0)

        // ON CONNECTION
        connection.connect()
        val contentLength = connection.contentLength
        val bytesTotal = contentLength + resumeLength
        if (bytesTotal < 5000000) return ERROR_TOO_SMALL_CONNECTION // DATA IS LESS THAN 5MB, SOMETHING IS WRONG

        context.setKey(KEY_DOWNLOAD_INFO, ep.id.toString(), DownloadedFileInfo(bytesTotal, relativePath, displayName))

        // Could use connection.contentType for mime types when creating the file,
        // however file is already created and players don't go of file type

        // https://stackoverflow.com/questions/23714383/what-are-all-the-possible-values-for-http-content-type-header
        // might receive application/octet-stream
        /*if (!connection.contentType.isNullOrEmpty() && !connection.contentType.startsWith("video")) {
            return ERROR_WRONG_CONTENT // CONTENT IS NOT VIDEO, SHOULD NEVER HAPPENED, BUT JUST IN CASE
        }*/

        // READ DATA FROM CONNECTION
        val connectionInputStream: InputStream = BufferedInputStream(connection.inputStream)
        val buffer = ByteArray(1024)
        var count: Int
        var bytesDownloaded = resumeLength


        var isPaused = false
        var isStopped = false
        var isDone = false
        var isFailed = false

        // TO NOT REUSE CODE
        fun updateNotification() {
            val type = when {
                isDone -> DownloadType.IsDone
                isStopped -> DownloadType.IsStopped
                isFailed -> DownloadType.IsFailed
                isPaused -> DownloadType.IsPaused
                else -> DownloadType.IsDownloading
            }

            try {
                downloadStatus[ep.id] = type
                downloadStatusEvent.invoke(Pair(ep.id, type))
            } catch (e: Exception) {
                // IDK MIGHT ERROR
            }

            createNotification(
                context,
                source,
                link.name,
                ep,
                type,
                bytesDownloaded,
                bytesTotal
            )
        }

        downloadEvent += { event ->
            if (event.first == ep.id) {
                when (event.second) {
                    DownloadActionType.Pause -> {
                        isPaused = true; updateNotification()
                    }
                    DownloadActionType.Stop -> {
                        isStopped = true; updateNotification()
                    }
                    DownloadActionType.Resume -> {
                        isPaused = false; updateNotification()
                    }
                }
            }
        }

        // UPDATE DOWNLOAD NOTIFICATION
        val notificationCoroutine = main {
            while (true) {
                if (!isPaused) {
                    updateNotification()
                }
                for (i in 1..10) {
                    delay(100)
                }
            }
        }

        val id = ep.id
        // THE REAL READ
        try {
            while (true) {
                count = connectionInputStream.read(buffer)
                if (count < 0) break
                bytesDownloaded += count
                downloadProgressEvent.invoke(Pair(id, bytesDownloaded))
                while (isPaused) {
                    sleep(100)
                    if (isStopped) {
                        break
                    }
                }
                if (isStopped) {
                    break
                }
                fileStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            isFailed = true
            updateNotification()
        }

        // REMOVE AND EXIT ALL
        fileStream.close()
        connectionInputStream.close()
        notificationCoroutine.cancel()

        try {
            downloadStatus.remove(ep.id)
        } catch (e: Exception) {
            // IDK MIGHT ERROR
        }

        // RETURN MESSAGE
        return when {
            isFailed -> {
                ERROR_CONNECTION_ERROR
            }
            isStopped -> {
                deleteFile()
            }
            else -> {
                isDone = true
                updateNotification()
                SUCCESS_DOWNLOAD_DONE
            }
        }
    }

    private fun downloadCheck(context: Context) {
        if (currentDownloads.size < maxConcurrentDownloads && downloadQueue.size > 0) {
            val pkg = downloadQueue.removeFirst()
            val item = pkg.item
            val id = item.ep.id
            if (currentDownloads.contains(id)) { // IF IT IS ALREADY DOWNLOADING, RESUME IT
                downloadEvent.invoke(Pair(id, DownloadActionType.Resume))
                return
            }

            val dQueue = downloadQueue.toList().mapIndexed { index, any -> DownloadQueueResumePackage(index, any) }
            context.setKey(KEY_RESUME_QUEUE_PACKAGES, dQueue)
            currentDownloads.add(id)

            main {
                try {
                    for (index in (pkg.linkIndex ?: 0) until item.links.size) {
                        val link = item.links[index]
                        val resume = pkg.linkIndex == index

                        context.setKey(KEY_RESUME_PACKAGES, id.toString(), DownloadResumePackage(item, index))
                        val connectionResult = withContext(Dispatchers.IO) {
                            normalSafeApiCall {
                                downloadSingleEpisode(context, item.source, item.folder, item.ep, link, resume)
                            }
                        }
                        if (connectionResult != null && connectionResult > 0) { // SUCCESS
                            context.removeKey(KEY_RESUME_PACKAGES, id.toString())
                            break
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    currentDownloads.remove(id)
                    downloadCheck(context)
                }
            }
        }
    }

    fun getDownloadFileInfoAndUpdateSettings(context: Context, id: Int): DownloadedFileInfoResult? {
        val res = getDownloadFileInfo(context, id)
        if (res == null) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return res
    }

    private fun getDownloadFileInfo(context: Context, id: Int): DownloadedFileInfoResult? {
        val info = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return null

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return null
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName) ?: return null
            val fileLength = cr.getFileLength(fileUri)
            if (fileLength == 0L) return null
            return DownloadedFileInfoResult(fileLength, info.totalBytes, fileUri)
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${info.relativePath}${info.displayName}".replace(
                    '/',
                    File.separatorChar
                )
            val dFile = File(normalPath)
            if (!dFile.exists()) return null
            return DownloadedFileInfoResult(dFile.length(), info.totalBytes, dFile.toUri())
        }
    }

    fun deleteFileAndUpdateSettings(context: Context, id: Int): Boolean {
        val success = deleteFile(context, id)
        if (success) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return success
    }

    private fun deleteFile(context: Context, id: Int): Boolean {
        val info = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return false

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return false
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName)
                    ?: return true // FILE NOT FOUND, ALREADY DELETED

            return cr.delete(fileUri, null, null) > 0 // IF DELETED ROWS IS OVER 0
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${info.relativePath}${info.displayName}".replace(
                    '/',
                    File.separatorChar
                )
            val dFile = File(normalPath)
            if (!dFile.exists()) return true
            return dFile.delete()
        }
    }

    fun getDownloadResumePackage(context: Context, id: Int): DownloadResumePackage? {
        return context.getKey(KEY_RESUME_PACKAGES, id.toString())
    }

    fun downloadFromResume(context: Context, pkg: DownloadResumePackage) {
        downloadQueue.addLast(pkg)
        downloadCheck(context)
    }

    fun isMyServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun downloadEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        links: List<ExtractorLink>
    ) {
        val validLinks = links.filter { !it.isM3u8 }
        if (validLinks.isNotEmpty()) {
            downloadFromResume(context, DownloadResumePackage(DownloadItem(source, folder, ep, validLinks), null))
        }
    }
}