package com.lagradost.cloudstream3.utils

import android.app.Notification
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
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.services.VideoDownloadService
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.util.*
import kotlin.math.roundToInt

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

    interface IDownloadableMinimum {
        val url: String
        val referer: String
        val headers: Map<String, String>
    }

    fun IDownloadableMinimum.getId(): Int {
        return url.hashCode()
    }

    data class DownloadEpisodeMetadata(
        @JsonProperty("id") val id: Int,
        @JsonProperty("mainName") val mainName: String,
        @JsonProperty("sourceApiName") val sourceApiName: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("type") val type: TvType?,
    )

    data class DownloadItem(
        @JsonProperty("source") val source: String?,
        @JsonProperty("folder") val folder: String?,
        @JsonProperty("ep") val ep: DownloadEpisodeMetadata,
        @JsonProperty("links") val links: List<ExtractorLink>,
    )

    data class DownloadResumePackage(
        @JsonProperty("item") val item: DownloadItem,
        @JsonProperty("linkIndex") val linkIndex: Int?,
    )

    data class DownloadedFileInfo(
        @JsonProperty("totalBytes") val totalBytes: Long,
        @JsonProperty("relativePath") val relativePath: String,
        @JsonProperty("displayName") val displayName: String,
        @JsonProperty("extraInfo") val extraInfo: String? = null,
        @JsonProperty("basePath") val basePath: String? = null // null is for legacy downloads. See getDefaultPath()
    )

    data class DownloadedFileInfoResult(
        @JsonProperty("fileLength") val fileLength: Long,
        @JsonProperty("totalBytes") val totalBytes: Long,
        @JsonProperty("path") val path: Uri,
    )

    data class DownloadQueueResumePackage(
        @JsonProperty("index") val index: Int,
        @JsonProperty("pkg") val pkg: DownloadResumePackage,
    )

    private const val SUCCESS_DOWNLOAD_DONE = 1
    private const val SUCCESS_STREAM = 3
    private const val SUCCESS_STOPPED = 2

    // will not download the next one, but is still classified as an error
    private const val ERROR_DELETING_FILE = 3
    private const val ERROR_CREATE_FILE = -2
    private const val ERROR_UNKNOWN = -10

    //private const val ERROR_OPEN_FILE = -3
    private const val ERROR_TOO_SMALL_CONNECTION = -4

    //private const val ERROR_WRONG_CONTENT = -5
    private const val ERROR_CONNECTION_ERROR = -6

    //private const val ERROR_MEDIA_STORE_URI_CANT_BE_CREATED = -7
    //private const val ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM = -8
    private const val ERROR_CONTENT_RESOLVER_NOT_FOUND = -9

    private const val KEY_RESUME_PACKAGES = "download_resume"
    const val KEY_DOWNLOAD_INFO = "download_info"
    private const val KEY_RESUME_QUEUE_PACKAGES = "download_q_resume"

    val downloadStatus = HashMap<Int, DownloadType>()
    val downloadStatusEvent = Event<Pair<Int, DownloadType>>()
    val downloadDeleteEvent = Event<Int>()
    val downloadEvent = Event<Pair<Int, DownloadActionType>>()
    val downloadProgressEvent = Event<Triple<Int, Long, Long>>()
    val downloadQueue = LinkedList<DownloadResumePackage>()

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

    /** Will return IsDone if not found or error */
    fun getDownloadState(id: Int): DownloadType {
        return try {
            downloadStatus[id] ?: DownloadType.IsDone
        } catch (e: Exception) {
            logError(e)
            DownloadType.IsDone
        }
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    private fun Context.getImageBitmapFromUrl(url: String): Bitmap? {
        try {
            if (cachedBitmaps.containsKey(url)) {
                return cachedBitmaps[url]
            }

            val bitmap = GlideApp.with(this)
                .asBitmap()
                .load(url).into(720, 720)
                .get()
            if (bitmap != null) {
                cachedBitmaps[url] = bitmap
            }
            return null
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    /**
     * @param hlsProgress will together with hlsTotal display another notification if used, to lessen the confusion about estimated size.
     * */
    private suspend fun createNotification(
        context: Context,
        source: String?,
        linkName: String?,
        ep: DownloadEpisodeMetadata,
        state: DownloadType,
        progress: Long,
        total: Long,
        notificationCallback: (Int, Notification) -> Unit,
        hlsProgress: Long? = null,
        hlsTotal: Long? = null,

        ): Notification? {
        try {
            if (total <= 0) return null// crash, invalid data

//        main { // DON'T WANT TO SLOW IT DOWN
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
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
                val pendingIntent: PendingIntent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                    } else {
                        PendingIntent.getActivity(context, 0, intent, 0)
                    }
                builder.setContentIntent(pendingIntent)
            }

            if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                builder.setProgress((total / 1000).toInt(), (progress / 1000).toInt(), false)
            }

            val rowTwoExtra = if (ep.name != null) " - ${ep.name}\n" else ""
            val rowTwo = if (ep.season != null && ep.episode != null) {
                "${context.getString(R.string.season_short)}${ep.season}:${context.getString(R.string.episode_short)}${ep.episode}" + rowTwoExtra
            } else if (ep.episode != null) {
                "${context.getString(R.string.episode)} ${ep.episode}" + rowTwoExtra
            } else {
                (ep.name ?: "") + ""
            }
            val downloadFormat = context.getString(R.string.download_format)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ep.poster != null) {
                    val poster = withContext(Dispatchers.IO) {
                        context.getImageBitmapFromUrl(ep.poster)
                    }
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }

                val progressPercentage: Long
                val progressMbString: String
                val totalMbString: String
                val suffix: String

                if (hlsProgress != null && hlsTotal != null) {
                    progressPercentage = hlsProgress.toLong() * 100 / hlsTotal
                    progressMbString = hlsProgress.toString()
                    totalMbString = hlsTotal.toString()
                    suffix = " - %.1f MB".format(progress / 1000000f)
                } else {
                    progressPercentage = progress * 100 / total
                    progressMbString = "%.1f MB".format(progress / 1000000f)
                    totalMbString = "%.1f MB".format(total / 1000000f)
                    suffix = ""
                }

                val bigText =
                    if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                        (if (linkName == null) "" else "$linkName\n") + "$rowTwo\n$progressPercentage % ($progressMbString/$totalMbString)$suffix"
                    } else if (state == DownloadType.IsFailed) {
                        downloadFormat.format(context.getString(R.string.download_failed), rowTwo)
                    } else if (state == DownloadType.IsDone) {
                        downloadFormat.format(context.getString(R.string.download_done), rowTwo)
                    } else {
                        downloadFormat.format(context.getString(R.string.download_canceled), rowTwo)
                    }

                val bodyStyle = NotificationCompat.BigTextStyle()
                bodyStyle.bigText(bigText)
                builder.setStyle(bodyStyle)
            } else {
                val txt =
                    if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                        rowTwo
                    } else if (state == DownloadType.IsFailed) {
                        downloadFormat.format(context.getString(R.string.download_failed), rowTwo)
                    } else if (state == DownloadType.IsDone) {
                        downloadFormat.format(context.getString(R.string.download_done), rowTwo)
                    } else {
                        downloadFormat.format(context.getString(R.string.download_canceled), rowTwo)
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
                        context, (4337 + index * 1000000 + ep.id),
                        actionResultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                DownloadActionType.Resume -> pressToResumeIcon
                                DownloadActionType.Pause -> pressToPauseIcon
                                DownloadActionType.Stop -> pressToStopIcon
                            }, when (i) {
                                DownloadActionType.Resume -> context.getString(R.string.resume)
                                DownloadActionType.Pause -> context.getString(R.string.pause)
                                DownloadActionType.Stop -> context.getString(R.string.cancel)
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            val notification = builder.build()
            notificationCallback(ep.id, notification)
            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(ep.id, notification)
            }
            return notification
        } catch (e: Exception) {
            logError(e)
            return null
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
    private fun ContentResolver.getExistingFolderStartName(relativePath: String): List<Pair<String, Uri>>? {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
                //MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath'"

            val result = this.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, null, null
            )
            val list = ArrayList<Pair<String, Uri>>()

            result.use { c ->
                if (c != null && c.count >= 1) {
                    c.moveToFirst()
                    while (true) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val name =
                            c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        list.add(Pair(name, uri))
                        if (c.isLast) {
                            break
                        }
                        c.moveToNext()
                    }

                    /*
                    val cDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val cRelativePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))*/

                }
            }
            return list
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    /**
     * Used for getting video player subs.
     * @return List of pairs for the files in this format: <Name, Uri>
     * */
    fun getFolder(
        context: Context,
        relativePath: String,
        basePath: String?
    ): List<Pair<String, Uri>>? {
        val base = basePathToFile(context, basePath)
        val folder = base?.gotoDir(relativePath, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && base.isDownloadDir()) {
            return context.contentResolver?.getExistingFolderStartName(relativePath)
        } else {
//            val normalPath =
//                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${relativePath}".replace(
//                    '/',
//                    File.separatorChar
//                )
//            val folder = File(normalPath)
            if (folder?.isDirectory == true) {
                return folder.listFiles()?.map { Pair(it.name ?: "", it.uri) }
            }
        }
        return null
//        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.getExistingDownloadUriOrNullQ(
        relativePath: String,
        displayName: String
    ): Uri? {
        try {
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
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun ContentResolver.getFileLength(fileUri: Uri): Long? {
        return try {
            this.openFileDescriptor(fileUri, "r")
                .use { it?.statSize ?: 0 }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    data class CreateNotificationMetadata(
        val type: DownloadType,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val hlsProgress: Long? = null,
        val hlsTotal: Long? = null,
    )

    data class StreamData(
        val errorCode: Int,
        val resume: Boolean? = null,
        val fileLength: Long? = null,
        val fileStream: OutputStream? = null,
    )

    /**
     * Sets up the appropriate file and creates a data stream from the file.
     * Used for initializing downloads.
     * */
    fun setupStream(
        context: Context,
        name: String,
        folder: String?,
        extension: String,
        tryResume: Boolean,
    ): StreamData {
        val displayName = getDisplayName(name, extension)
        val fileStream: OutputStream
        val fileLength: Long
        var resume = tryResume
        val baseFile = context.getBasePath()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && baseFile.first?.isDownloadDir() == true) {
            val cr = context.contentResolver ?: return StreamData(ERROR_CONTENT_RESOLVER_NOT_FOUND)

            val currentExistingFile =
                cr.getExistingDownloadUriOrNullQ(
                    folder ?: "",
                    displayName
                ) // CURRENT FILE WITH THE SAME PATH

            fileLength =
                if (currentExistingFile == null || !resume) 0 else (cr.getFileLength(
                    currentExistingFile
                )
                    ?: 0)// IF NOT RESUME THEN 0, OTHERWISE THE CURRENT FILE SIZE

            if (!resume && currentExistingFile != null) { // DELETE FILE IF FILE EXITS AND NOT RESUME
                val rowsDeleted = context.contentResolver.delete(currentExistingFile, null, null)
                if (rowsDeleted < 1) {
                    println("ERROR DELETING FILE!!!")
                }
            }

            var appendFile = false
            val newFileUri = if (resume && currentExistingFile != null) {
                appendFile = true
                currentExistingFile
            } else {
                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
                //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                val currentMimeType = when (extension) {

                    // Absolutely ridiculous, if text/vtt is used as mimetype scoped storage prevents
                    // downloading to /Downloads yet it works with null

                    "vtt" -> null // "text/vtt"
                    "mp4" -> "video/mp4"
                    "srt" -> null // "application/x-subrip"//"text/plain"
                    else -> null
                }
                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, name)
                    if (currentMimeType != null)
                        put(MediaStore.MediaColumns.MIME_TYPE, currentMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                }

                cr.insert(
                    contentUri,
                    newFile
                ) ?: return StreamData(ERROR_CONTENT_RESOLVER_NOT_FOUND)
            }

            fileStream = cr.openOutputStream(newFileUri, "w" + (if (appendFile) "a" else ""))
                ?: return StreamData(ERROR_CONTENT_RESOLVER_NOT_FOUND)
        } else {
            val subDir = baseFile.first?.gotoDir(folder)
            val rFile = subDir?.findFile(displayName)
            if (rFile?.exists() != true) {
                fileLength = 0
                if (subDir?.createFile(displayName) == null) return StreamData(ERROR_CREATE_FILE)
            } else {
                if (resume) {
                    fileLength = rFile.size()
                } else {
                    fileLength = 0
                    if (!rFile.delete()) return StreamData(ERROR_DELETING_FILE)
                    if (subDir.createFile(displayName) == null) return StreamData(ERROR_CREATE_FILE)
                }
            }
            fileStream = (subDir.findFile(displayName)
                ?: subDir.createFile(displayName))!!.openOutputStream()
//          fileStream = FileOutputStream(rFile, false)
            if (fileLength == 0L) resume = false
        }
        return StreamData(SUCCESS_STREAM, resume, fileLength, fileStream)
    }

    fun downloadThing(
        context: Context,
        link: IDownloadableMinimum,
        name: String,
        folder: String?,
        extension: String,
        tryResume: Boolean,
        parentId: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit,
    ): Int {
        if (link.url.startsWith("magnet") || link.url.endsWith(".torrent")) {
            return ERROR_UNKNOWN
        }

        val basePath = context.getBasePath()

        val displayName = getDisplayName(name, extension)
        val relativePath =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && basePath.first.isDownloadDir()) getRelativePath(
                folder
            ) else folder

        fun deleteFile(): Int {
            return delete(context, name, relativePath, extension, parentId, basePath.first)
        }

        val stream = setupStream(context, name, relativePath, extension, tryResume)
        if (stream.errorCode != SUCCESS_STREAM) return stream.errorCode

        val resume = stream.resume!!
        val fileStream = stream.fileStream!!
        val fileLength = stream.fileLength!!

        // CONNECT
        val connection: URLConnection =
            URL(link.url.replace(" ", "%20")).openConnection() // IDK OLD PHONES BE WACK

        // SET CONNECTION SETTINGS
        connection.connectTimeout = 10000
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("user-agent", USER_AGENT)
        if (link.referer.isNotEmpty()) connection.setRequestProperty("referer", link.referer)

        // extra stuff
        connection.setRequestProperty(
            "sec-ch-ua",
            "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\""
        )

        connection.setRequestProperty("sec-ch-ua-mobile", "?0")
        connection.setRequestProperty("accept", "*/*")
        //   dataSource.setRequestProperty("Sec-Fetch-Site", "none") //same-site
        connection.setRequestProperty("sec-fetch-user", "?1")
        connection.setRequestProperty("sec-fetch-mode", "navigate")
        connection.setRequestProperty("sec-fetch-dest", "video")
        link.headers.entries.forEach {
            connection.setRequestProperty(it.key, it.value)
        }

        if (resume)
            connection.setRequestProperty("Range", "bytes=${fileLength}-")
        val resumeLength = (if (resume) fileLength else 0)

        // ON CONNECTION
        connection.connect()

        val contentLength = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // fuck android
                connection.contentLengthLong
            } else {
                connection.getHeaderField("content-length").toLongOrNull()
                    ?: connection.contentLength.toLong()
            }
        } catch (e: Exception) {
            logError(e)
            0L
        }
        val bytesTotal = contentLength + resumeLength

        if (extension == "mp4" && bytesTotal < 5000000) return ERROR_TOO_SMALL_CONNECTION // DATA IS LESS THAN 5MB, SOMETHING IS WRONG

        parentId?.let {
            setKey(
                KEY_DOWNLOAD_INFO,
                it.toString(),
                DownloadedFileInfo(
                    bytesTotal,
                    relativePath ?: "",
                    displayName,
                    basePath = basePath.second
                )
            )
        }

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

            parentId?.let { id ->
                try {
                    downloadStatus[id] = type
                    downloadStatusEvent.invoke(Pair(id, type))
                    downloadProgressEvent.invoke(Triple(id, bytesDownloaded, bytesTotal))
                } catch (e: Exception) {
                    // IDK MIGHT ERROR
                }
            }

            createNotificationCallback.invoke(
                CreateNotificationMetadata(
                    type,
                    bytesDownloaded,
                    bytesTotal
                )
            )
            /*createNotification(
                context,
                source,
                link.name,
                ep,
                type,
                bytesDownloaded,
                bytesTotal
            )*/
        }

        val downloadEventListener = { event: Pair<Int, DownloadActionType> ->
            if (event.first == parentId) {
                when (event.second) {
                    DownloadActionType.Pause -> {
                        isPaused = true; updateNotification()
                    }
                    DownloadActionType.Stop -> {
                        isStopped = true; updateNotification()
                        removeKey(KEY_RESUME_PACKAGES, event.first.toString())
                        saveQueue()
                    }
                    DownloadActionType.Resume -> {
                        isPaused = false; updateNotification()
                    }
                }
            }
        }

        if (parentId != null)
            downloadEvent += downloadEventListener

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

        // THE REAL READ
        try {
            while (true) {
                count = connectionInputStream.read(buffer)
                if (count < 0) break
                bytesDownloaded += count
                // downloadProgressEvent.invoke(Pair(id, bytesDownloaded)) // Updates too much for any UI to keep up with
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
            logError(e)
            isFailed = true
            updateNotification()
        }

        // REMOVE AND EXIT ALL
        fileStream.close()
        connectionInputStream.close()
        notificationCoroutine.cancel()

        try {
            if (parentId != null)
                downloadEvent -= downloadEventListener
        } catch (e: Exception) {
            logError(e)
        }

        try {
            parentId?.let {
                downloadStatus.remove(it)
            }
        } catch (e: Exception) {
            // IDK MIGHT ERROR
        }

        // RETURN MESSAGE
        return when {
            isFailed -> {
                parentId?.let { id -> downloadProgressEvent.invoke(Triple(id, 0, 0)) }
                ERROR_CONNECTION_ERROR
            }
            isStopped -> {
                parentId?.let { id -> downloadProgressEvent.invoke(Triple(id, 0, 0)) }
                deleteFile()
            }
            else -> {
                parentId?.let { id ->
                    downloadProgressEvent.invoke(
                        Triple(
                            id,
                            bytesDownloaded,
                            bytesTotal
                        )
                    )
                }
                isDone = true
                updateNotification()
                SUCCESS_DOWNLOAD_DONE
            }
        }
    }


    /**
     * Guarantees a directory is present with the dir name (if createMissingDirectories is true).
     * Works recursively when '/' is present.
     * Will remove any file with the dir name if present and add directory.
     * Will not work if the parent directory does not exist.
     *
     * @param directoryName if null will use the current path.
     * @return UniFile / null if createMissingDirectories = false and folder is not found.
     * */
    private fun UniFile.gotoDir(
        directoryName: String?,
        createMissingDirectories: Boolean = true
    ): UniFile? {

        // May give this error on scoped storage.
        // W/DocumentsContract: Failed to create document
        // java.lang.IllegalArgumentException: Parent document isn't a directory

        // Not present in latest testing.

//        println("Going to dir $directoryName from ${this.uri} ---- ${this.filePath}")

        try {
            // Creates itself from parent if doesn't exist.
            if (!this.exists() && createMissingDirectories && !this.name.isNullOrBlank()) {
                if (this.parentFile != null) {
                    this.parentFile?.createDirectory(this.name)
                } else if (this.filePath != null) {
                    UniFile.fromFile(File(this.filePath!!).parentFile)?.createDirectory(this.name)
                }
            }

            val allDirectories = directoryName?.split("/")
            return if (allDirectories?.size == 1 || allDirectories == null) {
                val found = this.findFile(directoryName)
                when {
                    directoryName.isNullOrBlank() -> this
                    found?.isDirectory == true -> found

                    !createMissingDirectories -> null
                    // Below creates directories
                    found?.isFile == true -> {
                        found.delete()
                        this.createDirectory(directoryName)
                    }
                    this.isDirectory -> this.createDirectory(directoryName)
                    else -> this.parentFile?.createDirectory(directoryName)
                }
            } else {
                var currentDirectory = this
                allDirectories.forEach {
                    // If the next directory is not found it returns the deepest directory possible.
                    val nextDir = currentDirectory.gotoDir(it, createMissingDirectories)
                    currentDirectory = nextDir ?: return null
                }
                currentDirectory
            }
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    private fun getDisplayName(name: String, extension: String): String {
        return "$name.$extension"
    }

    /**
     * Gets the default download path as an UniFile.
     * Vital for legacy downloads, be careful about changing anything here.
     *
     * As of writing UniFile is used for everything but download directory on scoped storage.
     * Special ContentResolver fuckery is needed for that as UniFile doesn't work.
     * */
    fun getDownloadDir(): UniFile? {
        // See https://www.py4u.net/discuss/614761
        return UniFile.fromFile(
            File(
                Environment.getExternalStorageDirectory().absolutePath + File.separatorChar +
                        Environment.DIRECTORY_DOWNLOADS
            )
        )
    }

    @Deprecated("TODO fix UniFile to work with download directory.")
    private fun getRelativePath(folder: String?): String {
        return (Environment.DIRECTORY_DOWNLOADS + '/' + folder + '/').replace(
            '/',
            File.separatorChar
        )
    }

    /**
     * Turns a string to an UniFile. Used for stored string paths such as settings.
     * Should only be used to get a download path.
     * */
    private fun basePathToFile(context: Context, path: String?): UniFile? {
        return when {
            path.isNullOrBlank() -> getDownloadDir()
            path.startsWith("content://") -> UniFile.fromUri(context, path.toUri())
            else -> UniFile.fromFile(File(path))
        }
    }

    /**
     * Base path where downloaded things should be stored, changes depending on settings.
     * Returns the file and a string to be stored for future file retrieval.
     * UniFile.filePath is not sufficient for storage.
     * */
    fun Context.getBasePath(): Pair<UniFile?, String?> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val basePathSetting = settingsManager.getString(getString(R.string.download_path_key), null)
        return basePathToFile(this, basePathSetting) to basePathSetting
    }

    fun UniFile?.isDownloadDir(): Boolean {
        return this != null && this.filePath == getDownloadDir()?.filePath
    }

    private fun delete(
        context: Context,
        name: String,
        folder: String?,
        extension: String,
        parentId: Int?,
        basePath: UniFile?
    ): Int {
        val displayName = getDisplayName(name, extension)

        // delete all subtitle files
        if (extension == "mp4") {
            try {
                delete(context, name, folder, "vtt", parentId, basePath)
                delete(context, name, folder, "srt", parentId, basePath)
            } catch (e: Exception) {
                logError(e)
            }
        }

        // If scoped storage and using download dir (not accessible with UniFile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && basePath.isDownloadDir()) {
            val relativePath = getRelativePath(folder)
            val lastContent =
                context.contentResolver.getExistingDownloadUriOrNullQ(relativePath, displayName)
            if (lastContent != null) {
                context.contentResolver.delete(lastContent, null, null)
            }
        } else {
            val dir = basePath?.gotoDir(folder)
            val file = dir?.findFile(displayName)
            val success = file?.delete()
            if (success != true) return ERROR_DELETING_FILE else {
                // Cleans up empty directory
                if (dir.listFiles()?.isEmpty() == true) dir.delete()
            }
//        }
            parentId?.let {
                downloadDeleteEvent.invoke(parentId)
            }
        }
        return SUCCESS_STOPPED
    }

    private fun downloadHLS(
        context: Context,
        link: ExtractorLink,
        name: String,
        folder: String?,
        parentId: Int?,
        startIndex: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit
    ): Int {
        val extension = "mp4"
        fun logcatPrint(vararg items: Any?) {
            items.forEach {
                println("[HLS]: $it")
            }
        }

        val m3u8Helper = M3u8Helper()
        logcatPrint("initialised the HLS downloader.")

        val m3u8 = M3u8Helper.M3u8Stream(
            link.url, link.quality, mapOf("referer" to link.referer)
        )

        var realIndex = startIndex ?: 0
        val basePath = context.getBasePath()

        val relativePath =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && basePath.first.isDownloadDir()) getRelativePath(
                folder
            ) else folder

        val stream = setupStream(context, name, relativePath, extension, realIndex > 0)
        if (stream.errorCode != SUCCESS_STREAM) return stream.errorCode

        if (!stream.resume!!) realIndex = 0
        val fileLengthAdd = stream.fileLength!!
        val tsIterator = runBlocking {
            m3u8Helper.hlsYield(listOf(m3u8), realIndex)
        }

        val displayName = getDisplayName(name, extension)

        val fileStream = stream.fileStream!!

        val firstTs = tsIterator.next()

        var isDone = false
        var isFailed = false
        var isPaused = false
        var bytesDownloaded = firstTs.bytes.size.toLong() + fileLengthAdd
        var tsProgress = 1L + realIndex
        val totalTs = firstTs.totalTs.toLong()

        fun deleteFile(): Int {
            return delete(context, name, relativePath, extension, parentId, basePath.first)
        }
        /*
            Most of the auto generated m3u8 out there have TS of the same size.
            And only the last TS might have a different size.

            But oh well, in cases of handmade m3u8 streams this will go all over the place ¯\_(ツ)_/¯
            So ya, this calculates an estimate of how many bytes the file is going to be.

            > (bytesDownloaded/tsProgress)*totalTs
         */

        fun updateInfo() {
            parentId?.let {
                setKey(
                    KEY_DOWNLOAD_INFO,
                    it.toString(),
                    DownloadedFileInfo(
                        (bytesDownloaded * (totalTs / tsProgress.toFloat())).toLong(),
                        relativePath ?: "",
                        displayName,
                        tsProgress.toString(),
                        basePath = basePath.second
                    )
                )
            }
        }

        updateInfo()

        fun updateNotification() {
            val type = when {
                isDone -> DownloadType.IsDone
                isFailed -> DownloadType.IsFailed
                isPaused -> DownloadType.IsPaused
                else -> DownloadType.IsDownloading
            }

            parentId?.let { id ->
                try {
                    downloadStatus[id] = type
                    downloadStatusEvent.invoke(Pair(id, type))
                    downloadProgressEvent.invoke(
                        Triple(
                            id,
                            bytesDownloaded,
                            (bytesDownloaded * (totalTs / tsProgress.toFloat())).toLong(),
                        )
                    )
                } catch (e: Exception) {
                    // IDK MIGHT ERROR
                }
            }

            createNotificationCallback.invoke(
                CreateNotificationMetadata(
                    type,
                    bytesDownloaded,
                    (bytesDownloaded * (totalTs / tsProgress.toFloat())).toLong(),
                    tsProgress,
                    totalTs
                )
            )
        }

        fun stopIfError(ts: M3u8Helper.HlsDownloadData): Int? {
            if (ts.errored || ts.bytes.isEmpty()) {
                val error: Int = if (!ts.errored) {
                    logcatPrint("Error: No stream was found.")
                    ERROR_UNKNOWN
                } else {
                    logcatPrint("Error: Failed to fetch data.")
                    ERROR_CONNECTION_ERROR
                }
                isFailed = true
                fileStream.close()
                deleteFile()
                updateNotification()
                return error
            }
            return null
        }

        val notificationCoroutine = main {
            while (true) {
                if (!isDone) {
                    updateNotification()
                }
                for (i in 1..10) {
                    delay(100)
                }
            }
        }

        val downloadEventListener = { event: Pair<Int, DownloadActionType> ->
            if (event.first == parentId) {
                when (event.second) {
                    DownloadActionType.Stop -> {
                        isFailed = true
                    }
                    DownloadActionType.Pause -> {
                        isPaused =
                            true  // Pausing is not supported since well...I need to know the index of the ts it was paused at
                        // it may be possible to store it in a variable, but when the app restarts it will be lost
                    }
                    DownloadActionType.Resume -> {
                        isPaused = false
                    }
                }
                updateNotification()
            }
        }

        fun closeAll() {
            try {
                if (parentId != null)
                    downloadEvent -= downloadEventListener
            } catch (e: Exception) {
                logError(e)
            }
            try {
                parentId?.let {
                    downloadStatus.remove(it)
                }
            } catch (e: Exception) {
                logError(e)
                // IDK MIGHT ERROR
            }
            notificationCoroutine.cancel()
        }

        stopIfError(firstTs).let {
            if (it != null) {
                closeAll()
                return it
            }
        }

        if (parentId != null)
            downloadEvent += downloadEventListener

        fileStream.write(firstTs.bytes)

        fun onFailed() {
            fileStream.close()
            deleteFile()
            updateNotification()
            closeAll()
        }

        for (ts in tsIterator) {
            while (isPaused) {
                if (isFailed) {
                    onFailed()
                    return SUCCESS_STOPPED
                }
                sleep(100)
            }

            if (isFailed) {
                onFailed()
                return SUCCESS_STOPPED
            }

            stopIfError(ts).let {
                if (it != null) {
                    closeAll()
                    return it
                }
            }

            fileStream.write(ts.bytes)
            tsProgress = ts.currentIndex.toLong()
            bytesDownloaded += ts.bytes.size.toLong()
            logcatPrint("Download progress ${((tsProgress.toFloat() / totalTs.toFloat()) * 100).roundToInt()}%")
            updateInfo()
        }
        isDone = true
        fileStream.close()
        updateNotification()

        closeAll()
        updateInfo()
        return SUCCESS_DOWNLOAD_DONE
    }

    fun getFileName(context: Context, metadata: DownloadEpisodeMetadata): String {
        return getFileName(context, metadata.name, metadata.episode, metadata.season)
    }

    private fun getFileName(
        context: Context,
        epName: String?,
        episode: Int?,
        season: Int?
    ): String {
        // kinda ugly ik
        return sanitizeFilename(
            if (epName == null) {
                if (season != null) {
                    "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode"
                } else {
                    "${context.getString(R.string.episode)} $episode"
                }
            } else {
                if (episode != null) {
                    if (season != null) {
                        "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode - $epName"
                    } else {
                        "${context.getString(R.string.episode)} $episode - $epName"
                    }
                } else {
                    epName
                }
            }
        )
    }

    private fun downloadSingleEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        link: ExtractorLink,
        notificationCallback: (Int, Notification) -> Unit,
        tryResume: Boolean = false,
    ): Int {
        val name = getFileName(context, ep)

        // Make sure this is cancelled when download is done or cancelled.
        val extractorJob = ioSafe {
            if (link.extractorData != null) {
                getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
            }
        }

        if (link.isM3u8 || URI(link.url).path.endsWith(".m3u8")) {
            val startIndex = if (tryResume) {
                context.getKey<DownloadedFileInfo>(
                    KEY_DOWNLOAD_INFO,
                    ep.id.toString(),
                    null
                )?.extraInfo?.toIntOrNull()
            } else null
            return downloadHLS(context, link, name, folder, ep.id, startIndex) { meta ->
                main {
                    createNotification(
                        context,
                        source,
                        link.name,
                        ep,
                        meta.type,
                        meta.bytesDownloaded,
                        meta.bytesTotal,
                        notificationCallback,
                        meta.hlsProgress,
                        meta.hlsTotal
                    )
                }
            }.also { extractorJob.cancel() }
        }

        return normalSafeApiCall {
            downloadThing(context, link, name, folder, "mp4", tryResume, ep.id) { meta ->
                main {
                    createNotification(
                        context,
                        source,
                        link.name,
                        ep,
                        meta.type,
                        meta.bytesDownloaded,
                        meta.bytesTotal,
                        notificationCallback
                    )
                }
            }
        }.also { extractorJob.cancel() } ?: ERROR_UNKNOWN
    }

    fun downloadCheck(
        context: Context, notificationCallback: (Int, Notification) -> Unit,
    ): Int? {
        if (currentDownloads.size < maxConcurrentDownloads && downloadQueue.size > 0) {
            val pkg = downloadQueue.removeFirst()
            val item = pkg.item
            val id = item.ep.id
            if (currentDownloads.contains(id)) { // IF IT IS ALREADY DOWNLOADING, RESUME IT
                downloadEvent.invoke(Pair(id, DownloadActionType.Resume))
                /** ID needs to be returned to the work-manager to properly await notification */
                return id
            }

            currentDownloads.add(id)

            main {
                try {
                    for (index in (pkg.linkIndex ?: 0) until item.links.size) {
                        val link = item.links[index]
                        val resume = pkg.linkIndex == index

                        setKey(
                            KEY_RESUME_PACKAGES,
                            id.toString(),
                            DownloadResumePackage(item, index)
                        )
                        val connectionResult = withContext(Dispatchers.IO) {
                            normalSafeApiCall {
                                downloadSingleEpisode(
                                    context,
                                    item.source,
                                    item.folder,
                                    item.ep,
                                    link,
                                    notificationCallback,
                                    resume
                                ).also { println("Single episode finished with return code: $it") }
                            }
                        }
                        if (connectionResult != null && connectionResult > 0) { // SUCCESS
                            removeKey(KEY_RESUME_PACKAGES, id.toString())
                            break
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    currentDownloads.remove(id)
                    // Because otherwise notifications will not get caught by the workmanager
                    downloadCheckUsingWorker(context)
                }
            }
        }
        return null
    }

    fun getDownloadFileInfoAndUpdateSettings(context: Context, id: Int): DownloadedFileInfoResult? {
        val res = getDownloadFileInfo(context, id)
        if (res == null) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return res
    }

    private fun getDownloadFileInfo(context: Context, id: Int): DownloadedFileInfoResult? {
        try {
            val info =
                context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return null
            val base = basePathToFile(context, info.basePath)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && base.isDownloadDir()) {
                val cr = context.contentResolver ?: return null
                val fileUri =
                    cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName)
                        ?: return null
                val fileLength = cr.getFileLength(fileUri) ?: return null
                if (fileLength == 0L) return null
                return DownloadedFileInfoResult(fileLength, info.totalBytes, fileUri)
            } else {

                val file = base?.gotoDir(info.relativePath, false)?.findFile(info.displayName)

//            val normalPath = context.getNormalPath(getFile(info.relativePath), info.displayName)
//            val dFile = File(normalPath)

                if (file?.exists() != true) return null

                return DownloadedFileInfoResult(file.size(), info.totalBytes, file.uri)
            }
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    /**
     * Gets the true download size as Scoped Storage sometimes wrongly returns 0.
     * */
    fun UniFile.size(): Long {
        val len = length()
        return if (len <= 1) {
            val inputStream = this.openInputStream()
            return inputStream.available().toLong().also { inputStream.closeQuietly() }
        } else {
            len
        }
    }

    fun deleteFileAndUpdateSettings(context: Context, id: Int): Boolean {
        val success = deleteFile(context, id)
        if (success) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return success
    }

    private fun deleteFile(context: Context, id: Int): Boolean {
        val info =
            context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return false
        downloadEvent.invoke(Pair(id, DownloadActionType.Stop))
        downloadProgressEvent.invoke(Triple(id, 0, 0))
        downloadStatusEvent.invoke(Pair(id, DownloadType.IsStopped))
        downloadDeleteEvent.invoke(id)
        val base = basePathToFile(context, info.basePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && base.isDownloadDir()) {
            val cr = context.contentResolver ?: return false
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName)
                    ?: return true // FILE NOT FOUND, ALREADY DELETED

            return cr.delete(fileUri, null, null) > 0 // IF DELETED ROWS IS OVER 0
        } else {
            val file = base?.gotoDir(info.relativePath)?.findFile(info.displayName)
//            val normalPath = context.getNormalPath(getFile(info.relativePath), info.displayName)
//            val dFile = File(normalPath)
            if (file?.exists() != true) return true
            return try {
                file.delete()
            } catch (e: Exception) {
                logError(e)
                val cr = context.contentResolver
                cr.delete(file.uri, null, null) > 0
            }
        }
    }

    fun getDownloadResumePackage(context: Context, id: Int): DownloadResumePackage? {
        return context.getKey(KEY_RESUME_PACKAGES, id.toString())
    }

    fun downloadFromResume(
        context: Context,
        pkg: DownloadResumePackage,
        notificationCallback: (Int, Notification) -> Unit,
        setKey: Boolean = true
    ) {
        if (!currentDownloads.any { it == pkg.item.ep.id }) {
//            if (currentDownloads.size == maxConcurrentDownloads) {
//                main {
////                    showToast( // can be replaced with regular Toast
////                        context,
////                        "${pkg.item.ep.mainName}${pkg.item.ep.episode?.let { " ${context.getString(R.string.episode)} $it " } ?: " "}${
////                            context.getString(
////                                R.string.queued
////                            )
////                        }",
////                        Toast.LENGTH_SHORT
////                    )
//                }
//            }
            downloadQueue.addLast(pkg)
            downloadCheck(context, notificationCallback)
            if (setKey) saveQueue()
        } else {
            downloadEvent.invoke(
                Pair(pkg.item.ep.id, DownloadActionType.Resume)
            )
        }
    }

    private fun saveQueue() {
        try {
            val dQueue =
                downloadQueue.toList()
                    .mapIndexed { index, any -> DownloadQueueResumePackage(index, any) }
                    .toTypedArray()
            setKey(KEY_RESUME_QUEUE_PACKAGES, dQueue)
        } catch (e : Exception) {
            logError(e)
        }
    }

    /*fun isMyServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }*/

    fun downloadEpisode(
        context: Context?,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        links: List<ExtractorLink>,
        notificationCallback: (Int, Notification) -> Unit,
    ) {
        if (context == null) return
        if (links.isNotEmpty()) {
            downloadFromResume(
                context,
                DownloadResumePackage(DownloadItem(source, folder, ep, links), null),
                notificationCallback
            )
        }
    }

    /** Worker stuff */
    private fun startWork(context: Context, key: String) {
        val req = OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
            .setInputData(
                Data.Builder()
                    .putString("key", key)
                    .build()
            )
            .build()
        (WorkManager.getInstance(context)).enqueueUniqueWork(
            key,
            ExistingWorkPolicy.KEEP,
            req
        )
    }

    fun downloadCheckUsingWorker(
        context: Context,
    ) {
        startWork(context, DOWNLOAD_CHECK)
    }

    fun downloadFromResumeUsingWorker(
        context: Context,
        pkg: DownloadResumePackage,
    ) {
        val key = pkg.item.ep.id.toString()
        setKey(WORK_KEY_PACKAGE, key, pkg)
        startWork(context, key)
    }

    // Keys are needed to transfer the data to the worker reliably and without exceeding the data limit
    const val WORK_KEY_PACKAGE = "work_key_package"
    const val WORK_KEY_INFO = "work_key_info"

    fun downloadEpisodeUsingWorker(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        links: List<ExtractorLink>,
    ) {
        val info = DownloadInfo(
            source, folder, ep, links
        )

        val key = info.ep.id.toString()
        setKey(WORK_KEY_INFO, key, info)
        startWork(context, key)
    }

    data class DownloadInfo(
        @JsonProperty("source") val source: String?,
        @JsonProperty("folder") val folder: String?,
        @JsonProperty("ep") val ep: DownloadEpisodeMetadata,
        @JsonProperty("links") val links: List<ExtractorLink>
    )
}
