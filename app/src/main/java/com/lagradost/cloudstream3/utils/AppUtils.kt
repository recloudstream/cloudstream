package com.lagradost.cloudstream3.utils

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.tv.TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Spanned
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.*
import androidx.tvprovider.media.tv.WatchNextProgram.fromCursor
import androidx.viewpager2.widget.ViewPager2
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.wrappers.Wrappers
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.appStringResumeWatching
import com.lagradost.cloudstream3.ui.WebviewFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsViewModel.Companion.downloadAll
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.FillerEpisodeCheck.toClassDir
import com.lagradost.cloudstream3.utils.JsUnpacker.Companion.load
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import java.io.*
import java.net.URL
import java.net.URLDecoder
import kotlin.system.measureTimeMillis

object AppUtils {
    fun RecyclerView.setMaxViewPoolSize(maxViewTypeId: Int, maxPoolSize: Int) {
        for (i in 0..maxViewTypeId)
            recycledViewPool.setMaxRecycledViews(i, maxPoolSize)
    }

    fun RecyclerView.isRecyclerScrollable(): Boolean {
        val layoutManager =
            this.layoutManager as? LinearLayoutManager?
        val adapter = adapter
        return if (layoutManager == null || adapter == null) false else layoutManager.findLastCompletelyVisibleItemPosition() < adapter.itemCount - 7 // bit more than 1 to make it more seamless
    }

    fun BottomSheetDialog?.ownHide() {
        this?.hide()
    }

    fun BottomSheetDialog?.ownShow() {
        // the reason for this is because show has a shitty animation we don't want
        this?.window?.setWindowAnimations(-1)
        this?.show()
        Handler(Looper.getMainLooper()).postDelayed({
            this?.window?.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog)
        }, 200)
    }

    //fun Context.deleteFavorite(data: SearchResponse) {
    //    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    //    normalSafeApiCall {
    //        val existingId =
    //            getWatchNextProgramByVideoId(data.url, this).second ?: return@normalSafeApiCall
    //        contentResolver.delete(
//
    //            TvContractCompat.buildWatchNextProgramUri(existingId),
    //            null, null
    //        )
    //    }
    //}
    fun String?.html(): Spanned {
        return getHtmlText(this ?: return "".toSpanned())
    }

    private fun getHtmlText(text: String): Spanned {
        return try {
            // I have no idea if this can throw any error, but I dont want to try
            HtmlCompat.fromHtml(
                text, HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } catch (e: Exception) {
            logError(e)
            text.toSpanned()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun buildWatchNextProgramUri(
        context: Context,
        card: DataStoreHelper.ResumeWatchingResult,
        resumeWatching: VideoDownloadHelper.ResumeWatching?
    ): WatchNextProgram {
        val isSeries = card.type?.isMovieType() == false
        val title = if (isSeries) {
            context.getNameFull(card.name, card.episode, card.season)
        } else {
            card.name
        }

        val builder = WatchNextProgram.Builder()
            .setEpisodeTitle(title)
            .setType(
                if (isSeries) {
                    TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                } else TvContractCompat.WatchNextPrograms.TYPE_MOVIE
            )
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(title)
            .setPosterArtUri(Uri.parse(card.posterUrl))
            .setIntentUri(Uri.parse(card.id?.let {
                "$appStringResumeWatching://$it"
            } ?: card.url))
            .setInternalProviderId(card.url)
            .setLastEngagementTimeUtcMillis(
                resumeWatching?.updateTime ?: System.currentTimeMillis()
            )

        card.watchPos?.let {
            builder.setDurationMillis(it.duration.toInt())
            builder.setLastPlaybackPositionMillis(it.position.toInt())
        }

        if (isSeries)
            card.episode?.let {
                builder.setEpisodeNumber(it)
            }

        return builder.build()
    }

    // https://stackoverflow.com/a/67441735/13746422
    fun ViewPager2.reduceDragSensitivity(f: Int = 4) {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView

        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * f)       // "8" was obtained experimentally
    }

    fun ContentLoadingProgressBar?.animateProgressTo(to: Int) {
        if (this == null) return
        val animation: ObjectAnimator = ObjectAnimator.ofInt(
            this,
            "progress",
            this.progress,
            to
        )
        animation.duration = 500
        animation.setAutoCancel(true)
        animation.interpolator = DecelerateInterpolator()
        animation.start()
    }

    fun Context.createNotificationChannel(channelId: String, channelName: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(channelId, channelName, importance).apply {
                    this.description = description
                }

            // Register the channel with the system.
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("RestrictedApi")
    fun getAllWatchNextPrograms(context: Context): Set<Long> {
        val COLUMN_WATCH_NEXT_ID_INDEX = 0
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null
        )
        val set = mutableSetOf<Long>()
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    set.add(cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX))
                } while (it.moveToNext())
            }
        }
        return set
    }

    /**
     * Find the Watch Next program for given id.
     * Returns the first instance available.
     */
    @SuppressLint("RestrictedApi")
    // Suppress RestrictedApi due to https://issuetracker.google.com/138150076
    fun findFirstWatchNextProgram(context: Context, predicate: (Cursor) -> Boolean):
            Pair<WatchNextProgram?, Long?> {
        val COLUMN_WATCH_NEXT_ID_INDEX = 0
//        val COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX = 1
//        val COLUMN_WATCH_NEXT_COLUMN_BROWSABLE_INDEX = 2

        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    if (predicate(cursor)) {
                        return fromCursor(cursor) to cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX)
                    }
                } while (it.moveToNext())
            }
        }
        return null to null
    }

    /**
     * Query the Watch Next list and find the program with given videoId.
     * Return null if not found.
     */

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("Range")
    @Synchronized
    private fun getWatchNextProgramByVideoId(
        id: String,
        context: Context
    ): Pair<WatchNextProgram?, Long?> {
        return findFirstWatchNextProgram(context) { cursor ->
            (cursor.getString(cursor.getColumnIndex(COLUMN_INTERNAL_PROVIDER_ID)) == id)
        }
    }

    /** Prevents losing data when removing and adding simultaneously */
    private val continueWatchingLock = Mutex()

    // https://github.com/googlearchive/leanback-homescreen-channels/blob/master/app/src/main/java/com/google/android/tvhomescreenchannels/SampleTvProvider.java
    @SuppressLint("RestrictedApi")
    @WorkerThread
    suspend fun Context.addProgramsToContinueWatching(data: List<DataStoreHelper.ResumeWatchingResult>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = this
        continueWatchingLock.withLock {
            // A way to get all last watched timestamps
            val timeStampHashMap = HashMap<Int, VideoDownloadHelper.ResumeWatching>()
            getAllResumeStateIds()?.forEach { id ->
                val lastWatched = getLastWatched(id) ?: return@forEach
                timeStampHashMap[lastWatched.parentId] = lastWatched
            }

            val currentProgramIds = data.mapNotNull { episodeInfo ->
                try {
                    val customId = "${episodeInfo.id}|${episodeInfo.apiName}|${episodeInfo.url}"
                    val (program, id) = getWatchNextProgramByVideoId(customId, context)
                    val nextProgram = buildWatchNextProgramUri(
                        context,
                        episodeInfo,
                        timeStampHashMap[episodeInfo.id]
                    )

                    // If the program is already in the Watch Next row, update it
                    if (program != null && id != null) {
                        PreviewChannelHelper(context).updateWatchNextProgram(
                            nextProgram,
                            id,
                        )
                        id
                    } else {
                        PreviewChannelHelper(context)
                            .publishWatchNextProgram(nextProgram)
                    }
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }.toSet()

            val allOldPrograms = getAllWatchNextPrograms(context) - currentProgramIds

            // Ensures synced watch next progress by deleting all old programs.
            allOldPrograms.forEach {
                context.contentResolver.delete(
                    TvContractCompat.buildWatchNextProgramUri(it),
                    null, null
                )
            }
        }
    }

    @SuppressLint("Range")
    fun getVideoContentUri(context: Context, videoFilePath: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
            MediaStore.Video.Media.DATA + "=? ", arrayOf(videoFilePath), null
        )
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            cursor.close()
            Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id)
        } else {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DATA, videoFilePath)
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            )
        }
    }

    fun Activity.loadRepository(url: String) {
        ioSafe {
            val repo = RepositoryManager.parseRepository(url) ?: return@ioSafe
            RepositoryManager.addRepository(
                RepositoryData(
                    repo.name,
                    url
                )
            )
            main {
                showToast(
                    this@loadRepository,
                    getString(R.string.player_loaded_subtitles, repo.name),
                    Toast.LENGTH_LONG
                )
            }
            afterRepositoryLoadedEvent.invoke(true)
            downloadAllPluginsDialog(url, repo.name)
        }
    }

    abstract class DiffAdapter<T>(
        open val items: MutableList<T>,
        val comparison: (first: T, second: T) -> Boolean = { first, second ->
            first.hashCode() == second.hashCode()
        }
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int {
            return items.size
        }

        fun updateList(newList: List<T>) {
            val diffResult = DiffUtil.calculateDiff(
                GenericDiffCallback(this.items, newList)
            )

            items.clear()
            items.addAll(newList)

            diffResult.dispatchUpdatesTo(this)
        }

        inner class GenericDiffCallback(
            private val oldList: List<T>,
            private val newList: List<T>
        ) :
            DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                comparison(oldList[oldItemPosition], newList[newItemPosition])

            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = newList.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition] == newList[newItemPosition]
        }
    }


    fun Activity.downloadAllPluginsDialog(repositoryUrl: String, repositoryName: String) {
        runOnUiThread {
            val context = this
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(
                repositoryName
            )
            builder.setMessage(
                R.string.download_all_plugins_from_repo
            )
            builder.apply {
                setPositiveButton(R.string.download) { _, _ ->
                    downloadAll(context, repositoryUrl, null)
                }

                setNegativeButton(R.string.no) { _, _ -> }
            }
            builder.show().setDefaultFocus()
        }
    }

    private fun Context.hasWebView(): Boolean {
        return this.packageManager.hasSystemFeature("android.software.webview")
    }

    fun openWebView(fragment: Fragment?, url: String) {
        if (fragment?.context?.hasWebView() == true)
            normalSafeApiCall {
                fragment
                    .findNavController()
                    .navigate(R.id.navigation_webview, WebviewFragment.newInstance(url))
            }
    }

    /**
     * If fallbackWebview is true and a fragment is supplied then it will open a webview with the url if the browser fails.
     * */
    fun Context.openBrowser(
        url: String,
        fallbackWebview: Boolean = false,
        fragment: Fragment? = null,
    ) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

            // activityResultRegistry is used to fall back to webview if a browser is missing
            // On older versions the startActivity just crashes, but on newer android versions
            // You need to check the result to make sure it failed
            val activityResultRegistry = fragment?.activity?.activityResultRegistry
            if (activityResultRegistry != null) {
                activityResultRegistry.register(
                    url,
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_CANCELED && fallbackWebview) {
                        openWebView(fragment, url)
                    }
                }.launch(intent)
            } else {
                ContextCompat.startActivity(this, intent, null)
            }

        } catch (e: Exception) {
            logError(e)
            if (fallbackWebview) {
                openWebView(fragment, url)
            }
        }
    }

    fun Context.isNetworkAvailable(): Boolean {
        val manager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = manager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected || manager.allNetworkInfo?.any { it.isConnected } ?: false
    }

    fun splitQuery(url: URL): Map<String, String> {
        val queryPairs: MutableMap<String, String> = LinkedHashMap()
        val query: String = url.query
        val pairs = query.split("&").toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

    /** Any object as json string */
    fun Any.toJson(): String {
        if (this is String) return this
        return mapper.writeValueAsString(this)
    }

    inline fun <reified T> parseJson(value: String): T {
        return mapper.readValue(value)
    }

    inline fun <reified T> tryParseJson(value: String?): T? {
        return try {
            parseJson(value ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    /**| S1:E2 Hello World
     * | Episode 2. Hello world
     * | Hello World
     * | Season 1 - Episode 2
     * | Episode 2
     * **/
    fun Context.getNameFull(name: String?, episode: Int?, season: Int?): String {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season

        val seasonName = getString(R.string.season)
        val episodeName = getString(R.string.episode)
        val seasonNameShort = getString(R.string.season_short)
        val episodeNameShort = getString(R.string.episode_short)

        if (name != null) {
            return if (rEpisode != null && rSeason != null) {
                "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode} $name"
            } else if (rEpisode != null) {
                "$episodeName $rEpisode. $name"
            } else {
                name
            }
        } else {
            if (rEpisode != null && rSeason != null) {
                return "$seasonName $rSeason - $episodeName $rEpisode"
            } else if (rSeason == null) {
                return "$episodeName $rEpisode"
            }
        }
        return ""
    }

    fun Activity?.loadCache() {
        try {
            cacheClass("android.net.NetworkCapabilities".load())
        } catch (_: Exception) {
        }
    }

    //private val viewModel: ResultViewModel by activityViewModels()

    private fun getResultsId(): Int {
        return if (isTrueTvSettings()) {
            R.id.global_to_navigation_results_tv
        } else {
            R.id.global_to_navigation_results_phone
        }
    }

    fun FragmentActivity.loadResult(
        url: String,
        apiName: String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        this.runOnUiThread {
            // viewModelStore.clear()
            this.navigate(
                getResultsId(),
                ResultFragment.newInstance(url, apiName, startAction, startValue)
            )
        }
    }

    fun Activity?.loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
        this?.runOnUiThread {
            // viewModelStore.clear()
            this.navigate(
                getResultsId(),
                ResultFragment.newInstance(card, startAction, startValue)
            )
        }
        //(this as? AppCompatActivity?)?.loadResult(card.url, card.apiName, startAction, startValue)
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
        currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            )
        }
        return currentAudioFocusChangeListener
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            println(e)
            // track non-fatal
            return false
        }
        return isCastApiAvailable
    }

    fun Context.isConnectedToChromecast(): Boolean {
        if (isCastApiAvailable()) {
            val castContext = CastContext.getSharedInstance(this)
            if (castContext.castState == CastState.CONNECTED) {
                return true
            }
        }
        return false
    }

    /**
     * Sets the focus to the negative button when in TV and Emulator layout.
     **/
    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        if (!isTvSettings()) return
        this.getButton(buttonFocus).run {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    // Copied from https://github.com/videolan/vlc-android/blob/master/application/vlc-android/src/org/videolan/vlc/util/FileUtils.kt
    @SuppressLint("Range")
    fun Context.getUri(data: Uri?): Uri? {
        var uri = data
        val ctx = this
        if (data != null && data.scheme == "content") {
            // Mail-based apps - download the stream to a temporary file and play it
            if ("com.fsck.k9.attachmentprovider" == data.host || "gmail-ls" == data.host) {
                var inputStream: InputStream? = null
                var os: OutputStream? = null
                var cursor: Cursor? = null
                try {
                    cursor = ctx.contentResolver.query(
                        data,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        val filename =
                            cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                                .replace("/", "")
                        inputStream = ctx.contentResolver.openInputStream(data)
                        if (inputStream == null) return data
                        os =
                            FileOutputStream(Environment.getExternalStorageDirectory().path + "/Download/" + filename)
                        val buffer = ByteArray(1024)
                        var bytesRead = inputStream.read(buffer)
                        while (bytesRead >= 0) {
                            os.write(buffer, 0, bytesRead)
                            bytesRead = inputStream.read(buffer)
                        }
                        uri =
                            Uri.fromFile(File(Environment.getExternalStorageDirectory().path + "/Download/" + filename))
                    }
                } catch (e: Exception) {
                    return null
                } finally {
                    inputStream?.close()
                    os?.close()
                    cursor?.close()
                }
            } else if (data.authority == "media") {
                uri = this.contentResolver.query(
                    data,
                    arrayOf(MediaStore.Video.Media.DATA), null, null, null
                )?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    if (it.moveToFirst()) Uri.fromFile(File(it.getString(columnIndex)))
                        ?: data else data
                }
                //uri = MediaUtils.getContentMediaUri(data)
                /*} else if (data.authority == ctx.getString(R.string.tv_provider_authority)) {
                    println("TV AUTHORITY")
                    //val medialibrary = Medialibrary.getInstance()
                    //val media = medialibrary.getMedia(data.lastPathSegment!!.toLong())
                    uri = null//media.uri*/
            } else {
                val inputPFD: ParcelFileDescriptor?
                try {
                    inputPFD = ctx.contentResolver.openFileDescriptor(data, "r")
                    if (inputPFD == null) return data
                    uri = Uri.parse("fd://" + inputPFD.fd)
                    //                    Cursor returnCursor =
                    //                            getContentResolver().query(data, null, null, null, null);
                    //                    if (returnCursor != null) {
                    //                        if (returnCursor.getCount() > 0) {
                    //                            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    //                            if (nameIndex > -1) {
                    //                                returnCursor.moveToFirst();
                    //                                title = returnCursor.getString(nameIndex);
                    //                            }
                    //                        }
                    //                        returnCursor.close();
                    //                    }
                } catch (e: FileNotFoundException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalArgumentException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: IllegalStateException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: NullPointerException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                } catch (e: SecurityException) {
                    Log.e("TAG", "${e.message} for $data", e)
                    return null
                }
            }// Media or MMS URI
        }
        return uri
    }

    fun Context.isUsingMobileData(): Boolean {
        val conManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = conManager.allNetworks
        return networkInfo.any {
            conManager.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    } &&
                !networkInfo.any {
                    conManager.getNetworkCapabilities(it)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
        }


    private fun Activity?.cacheClass(clazz: String?) {
        clazz?.let { c ->
            this?.cacheDir?.let {
                Cache(
                    directory = File(it, c.toClassDir()),
                    maxSize = 20L * 1024L * 1024L // 20 MiB
                )
            }
        }
    }

    fun Context.isAppInstalled(uri: String): Boolean {
        val pm = Wrappers.packageManager(this)

        return try {
            pm.getPackageInfo(uri, 0) // PackageManager.GET_ACTIVITIES
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (currentAudioFocusRequest != null) return currentAudioFocusRequest
        currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    build()
                })
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let {
                    setOnAudioFocusChangeListener(it)
                }
                build()
            }
        } else {
            null
        }
        return currentAudioFocusRequest
    }
}
