package com.lagradost.cloudstream3.utils

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.tv.TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.Spanned
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.tvprovider.media.tv.WatchNextProgram.fromCursor
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.wrappers.Wrappers
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity.Companion.afterRepositoryLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING_RESUME_WATCHING
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.ui.WebviewFragment
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.settings.Globals
import com.lagradost.cloudstream3.ui.settings.extensions.PluginsFragment
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
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.net.Uri
import android.util.Log
import androidx.biometric.AuthenticationResult
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.lagradost.cloudstream3.SearchResponse
import android.content.ContentUris
import android.content.Intent




object AppContextUtils {
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

    fun View.isLtr() = this.layoutDirection == LAYOUT_DIRECTION_LTR
    fun View.isRtl() = this.layoutDirection == LAYOUT_DIRECTION_RTL

    fun BottomSheetDialog?.ownHide() {
        this?.hide()
    }

    fun BottomSheetDialog?.ownShow() {
        // the reason for this is because show has a shitty animation we don't want
        this?.window?.setWindowAnimations(-1)
        this?.show()
        Handler(Looper.getMainLooper()).postDelayed({
            this?.window?.setWindowAnimations(com.google.android.material.R.style.Animation_Design_BottomSheetDialog)
        }, 200)
    }

    //fun Context.deleteFavorite(data: SearchResponse) {
    //    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    //    safe {
    //        val existingId =
    //            getWatchNextProgramByVideoId(data.url, this).second ?: return@safe
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
    /** Get channel ID by name */
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
                "$APP_STRING_RESUME_WATCHING://$it"
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

    fun Context.createNotificationChannel(
        channelId: String,
        channelName: String,
        description: String
    ) {
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
    @Throws
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

    fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
        return subs.sortedBy { it.name }
    }

    fun Context.getApiSettings(): HashSet<String> {
        //val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val hashSet = HashSet<String>()
        val activeLangs = getApiProviderLangSettings()
        val hasUniversal = activeLangs.contains(AllLanguagesName)
        hashSet.addAll(synchronized(apis) { apis.filter { hasUniversal || activeLangs.contains(it.lang) } }
            .map { it.name })

        /*val set = settingsManager.getStringSet(
            this.getString(R.string.search_providers_list_key),
            hashSet
        )?.toHashSet() ?: hashSet

        val list = HashSet<String>()
        for (name in set) {
            val api = getApiFromNameNull(name) ?: continue
            if (activeLangs.contains(api.lang)) {
                list.add(name)
            }
        }*/
        //if (list.isEmpty()) return hashSet
        //return list
        return hashSet
    }

    fun Context.getApiDubstatusSettings(): HashSet<DubStatus> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<DubStatus>()
        hashSet.addAll(DubStatus.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.display_sub_key),
            hashSet.map { it.name }.toMutableSet()
        ) ?: return hashSet

        val names = DubStatus.values().map { it.name }.toHashSet()
        //if(realSet.isEmpty()) return hashSet

        return list.filter { names.contains(it) }.map { DubStatus.valueOf(it) }.toHashSet()
    }

    fun Context.getApiProviderLangSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = hashSetOf(AllLanguagesName) // def is all languages
//        hashSet.add("en") // def is only en
        val list = settingsManager.getStringSet(
            this.getString(R.string.provider_lang_key),
            hashSet
        )

        if (list.isNullOrEmpty()) return hashSet
        return list.toHashSet()
    }

    fun Context.getApiTypeSettings(): HashSet<TvType> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.search_types_list_key),
            hashSet.map { it.name }.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if (realSet.isEmpty()) return hashSet

        return realSet
    }

    fun Context.updateHasTrailers() {
        LoadResponse.isTrailersEnabled = getHasTrailers()
    }

    private fun Context.getHasTrailers(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getBoolean(this.getString(R.string.show_trailers_key), true)
    }

    fun Context.filterProviderByPreferredMedia(hasHomePageIsRequired: Boolean = true): List<MainAPI> {
        // We are getting the weirdest crash ever done:
        // java.lang.ClassCastException: com.lagradost.cloudstream3.TvType cannot be cast to com.lagradost.cloudstream3.TvType
        // Trying fixing using classloader fuckery
        val oldLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = TvType::class.java.classLoader

        val default = TvType.values()
            .sorted()
            .filter { it != TvType.NSFW }
            .map { it.ordinal }

        Thread.currentThread().contextClassLoader = oldLoader

        val defaultSet = default.map { it.toString() }.toSet()
        val currentPrefMedia = try {
            PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet(this.getString(R.string.prefer_media_type_key), defaultSet)
                ?.mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }
        } catch (e: Throwable) {
            null
        } ?: default
        val langs = this.getApiProviderLangSettings()
        val hasUniversal = langs.contains(AllLanguagesName)
        val allApis = synchronized(apis) {
            apis.filter { api -> (hasUniversal || langs.contains(api.lang)) && (api.hasMainPage || !hasHomePageIsRequired) }
        }
        return if (currentPrefMedia.isEmpty()) {
            allApis
        } else {
            // Filter API depending on preferred media type
            allApis.filter { api -> api.supportedTypes.any { currentPrefMedia.contains(it.ordinal) } }
        }
    }

    fun Context.filterSearchResultByFilmQuality(data: List<SearchResponse>): List<SearchResponse> {
        // Filter results omitting entries with certain quality
        if (data.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return data.filter { item ->
                    val searchQualVal = item.quality?.ordinal ?: -1
                    //Log.i("filterSearch", "QuickSearch item => ${item.toJson()}")
                    !filteredSearchQuality.contains(searchQualVal)
                }
            }
        }
        return data
    }

    fun Context.filterHomePageListByFilmQuality(data: HomePageList): HomePageList {
        // Filter results omitting entries with certain quality
        if (data.list.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return HomePageList(
                    name = data.name,
                    isHorizontalImages = data.isHorizontalImages,
                    list = data.list.filter { item ->
                        val searchQualVal = item.quality?.ordinal ?: -1
                        //Log.i("filterSearch", "QuickSearch item => ${item.toJson()}")
                        !filteredSearchQuality.contains(searchQualVal)
                    }
                )
            }
        }
        return data
    }

    fun Activity.loadRepository(url: String) {
        ioSafe {
            val repo = RepositoryManager.parseRepository(url) ?: return@ioSafe
            RepositoryManager.addRepository(
                RepositoryData(
                    repo.iconUrl ?: "",
                    repo.name,
                    url
                )
            )
            main {
                showToast(
                    getString(R.string.player_loaded_subtitles, repo.name),
                    Toast.LENGTH_LONG
                )
            }
            afterRepositoryLoadedEvent.invoke(true)
            addRepositoryDialog(repo.name, url)
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

    fun Activity.addRepositoryDialog(
        repositoryName: String,
        repositoryURL: String,
    ) {
        val repos = RepositoryManager.getRepositories()

        // navigate to newly added repository on pressing Open Repository
        fun openAddedRepo() {
            if (repos.isNotEmpty()) {
                navigate(
                    R.id.global_to_navigation_settings_plugins,
                    PluginsFragment.newInstance(
                        repositoryName,
                        repositoryURL,
                        false,
                    )
                )
            }
        }

        runOnUiThread {
            AlertDialog.Builder(this).apply {
                setTitle(repositoryName)
                setMessage(R.string.download_all_plugins_from_repo)
                setPositiveButton(R.string.open_downloaded_repo) { _, _ ->
                    openAddedRepo()
                }
                setNegativeButton(R.string.dismiss, null)
                show().setDefaultFocus()
            }
        }
    }

    private fun Context.hasWebView(): Boolean {
        return this.packageManager.hasSystemFeature("android.software.webview")
    }

    fun openWebView(fragment: Fragment?, url: String) {
        if (fragment?.context?.hasWebView() == true)
            safe {
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
    ) = (this.getActivity() ?: activity)?.runOnUiThread {
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
            } else this.startActivity(intent)
        } catch (e: Exception) {
            logError(e)
            if (fallbackWebview) {
                openWebView(fragment, url)
            }
        }
    }

    fun Context.isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
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
        return if (Globals.isLayout(Globals.TV or Globals.EMULATOR)) {
            R.id.global_to_navigation_results_tv
        } else {
            R.id.global_to_navigation_results_phone
        }
    }

    fun loadResult(
        url: String,
        apiName: String,
        name : String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        (activity as FragmentActivity?)?.loadResult(url, apiName, name, startAction, startValue)
    }

    fun FragmentActivity.loadResult(
        url: String,
        apiName: String,
        name : String,
        startAction: Int = 0,
        startValue: Int = 0
    ) {
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            Kitsu.isEnabled =
                settingsManager.getBoolean(this.getString(R.string.show_kitsu_posters_key), true)
        } catch (t: Throwable) {
            logError(t)
        }

        this.runOnUiThread {
            // viewModelStore.clear()
            this.navigate(
                getResultsId(),
                ResultFragment.newInstance(url, apiName, name, startAction, startValue)
            )
        }
    }

    fun loadSearchResult(
        card: SearchResponse,
        startAction: Int = 0,
        startValue: Int? = null,
    ) {
        activity?.loadSearchResult(card, startAction, startValue)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                Log.e("TAG", "focusRequest was null")
                return
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
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
            applicationContext?.let {
                val task = CastContext.getSharedInstance(it) { it.run() }
                task.result
            }
        } catch (e: Exception) {
            println(e)
            // Track non-fatal
            return false
        }

        return isCastApiAvailable
    }

    fun Context.isConnectedToChromecast(): Boolean {
        if (isCastApiAvailable()) {
            val executor: Executor = Executors.newSingleThreadExecutor()
            val castContext = CastContext.getSharedInstance(this, executor)
            if (castContext.result.castState == CastState.CONNECTED) {
                return true
            }
        }
        return false
    }

    /**
     * Sets the focus to the negative button when in TV and Emulator layout.
     **/
    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        if (!Globals.isLayout(Globals.TV or Globals.EMULATOR)) return
        this.getButton(buttonFocus).run {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    fun Context.isUsingMobileData(): Boolean {
        val connectionManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork: Network? = connectionManager.activeNetwork
            val networkCapabilities = connectionManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connectionManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
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
        } else null
        return currentAudioFocusRequest
    }
}