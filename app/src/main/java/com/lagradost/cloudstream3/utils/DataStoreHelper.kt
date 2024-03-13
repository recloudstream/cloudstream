package com.lagradost.cloudstream3.utils

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.ui.result.UiImage
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

const val VIDEO_POS_DUR = "video_pos_dur"
const val VIDEO_WATCH_STATE = "video_watch_state"
const val RESULT_WATCH_STATE = "result_watch_state"
const val RESULT_WATCH_STATE_DATA = "result_watch_state_data"
const val RESULT_SUBSCRIBED_STATE_DATA = "result_subscribed_state_data"
const val RESULT_FAVORITES_STATE_DATA = "result_favorites_state_data"
const val RESULT_RESUME_WATCHING = "result_resume_watching_2" // changed due to id changes
const val RESULT_RESUME_WATCHING_OLD = "result_resume_watching"
const val RESULT_RESUME_WATCHING_HAS_MIGRATED = "result_resume_watching_migrated"
const val RESULT_EPISODE = "result_episode"
const val RESULT_SEASON = "result_season"
const val RESULT_DUB = "result_dub"


class UserPreferenceDelegate<T : Any>(
    private val key: String, private val default: T //, private val klass: KClass<T>
) {
    private val klass: KClass<out T> = default::class
    private val realKey get() = "${DataStoreHelper.currentAccount}/$key"
    operator fun getValue(self: Any?, property: KProperty<*>) =
        AcraApplication.getKeyClass(realKey, klass.java) ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        if (t == null) {
            removeKey(realKey)
        } else {
            AcraApplication.setKeyClass(realKey, t)
        }
    }
}

object DataStoreHelper {
    // be aware, don't change the index of these as Account uses the index for the art
    val profileImages = arrayOf(
        R.drawable.profile_bg_dark_blue,
        R.drawable.profile_bg_blue,
        R.drawable.profile_bg_orange,
        R.drawable.profile_bg_pink,
        R.drawable.profile_bg_purple,
        R.drawable.profile_bg_red,
        R.drawable.profile_bg_teal
    )

    private var searchPreferenceProvidersStrings : List<String> by UserPreferenceDelegate(
        /** java moment right here, as listOf()::class.java != List(0) { "" }::class.java */
        "search_pref_providers", List(0) { "" }
    )

    private fun serializeTv(data : List<TvType>) : List<String> = data.map { it.name }

    private fun deserializeTv(data : List<String>) : List<TvType> {
        return data.mapNotNull { listName ->
            TvType.values().firstOrNull { it.name == listName }
        }
    }

    var searchPreferenceProviders : List<String>
        get() {
            val ret = searchPreferenceProvidersStrings
            return ret.ifEmpty {
                context?.filterProviderByPreferredMedia()?.map { it.name } ?: emptyList()
            }
        } set(value) {
            searchPreferenceProvidersStrings = value
        }

    private var searchPreferenceTagsStrings : List<String> by UserPreferenceDelegate("search_pref_tags", listOf(TvType.Movie, TvType.TvSeries).map { it.name })
    var searchPreferenceTags : List<TvType>
        get() = deserializeTv(searchPreferenceTagsStrings)
        set(value) {
            searchPreferenceTagsStrings = serializeTv(value)
        }


    private var homePreferenceStrings : List<String> by UserPreferenceDelegate("home_pref_homepage", listOf(TvType.Movie, TvType.TvSeries).map { it.name })
    var homePreference : List<TvType>
        get() = deserializeTv(homePreferenceStrings)
        set(value) {
            homePreferenceStrings = serializeTv(value)
        }

    var homeBookmarkedList : IntArray by UserPreferenceDelegate("home_bookmarked_last_list", IntArray(0))
    var playBackSpeed : Float by UserPreferenceDelegate("playback_speed", 1.0f)
    var resizeMode : Int by UserPreferenceDelegate("resize_mode", 0)
    var librarySortingMode : Int by UserPreferenceDelegate("library_sorting_mode", ListSorting.AlphabeticalA.ordinal)

    data class Account(
        @JsonProperty("keyIndex")
        val keyIndex: Int,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("customImage")
        val customImage: String? = null,
        @JsonProperty("defaultImageIndex")
        val defaultImageIndex: Int,
        @JsonProperty("lockPin")
        val lockPin: String? = null,
    ) {
        val image: UiImage
            get() = customImage?.let { UiImage.Image(it) } ?: UiImage.Drawable(
                profileImages.getOrNull(defaultImageIndex) ?: profileImages.first()
            )
    }

    const val TAG = "data_store_helper"
    var accounts by PreferenceDelegate("$TAG/account", arrayOf<Account>())
    var selectedKeyIndex by PreferenceDelegate("$TAG/account_key_index", 0)
    val currentAccount: String get() = selectedKeyIndex.toString()

    /**
     * Get or set the current account homepage.
     * Setting this does not automatically reload the homepage.
     */
    var currentHomePage: String?
        get() = getKey("$currentAccount/$USER_SELECTED_HOMEPAGE_API")
        set(value) {
            val key = "$currentAccount/$USER_SELECTED_HOMEPAGE_API"
            if (value == null) {
                removeKey(key)
            } else {
                setKey(key, value)
            }
        }

    fun setAccount(account: Account) {
        val homepage = currentHomePage

        selectedKeyIndex = account.keyIndex
        showToast(context?.getString(R.string.logged_account, account.name) ?: account.name)
        MainActivity.bookmarksUpdatedEvent(true)
        MainActivity.reloadLibraryEvent(true)
        val oldAccount = accounts.find { it.keyIndex == account.keyIndex }
        if (oldAccount != null && currentHomePage != homepage) {
            // This is not a new account, and the homepage has changed, reload it
            MainActivity.reloadHomeEvent(true)
        }
    }

    fun getDefaultAccount(context: Context): Account {
        return accounts.let { currentAccounts ->
            currentAccounts.getOrNull(currentAccounts.indexOfFirst { it.keyIndex == 0 }) ?: Account(
                keyIndex = 0,
                name = context.getString(R.string.default_account),
                defaultImageIndex = 0
            )
        }
    }

    fun getAccounts(context: Context): List<Account> {
        return accounts.toMutableList().apply {
            val item = getDefaultAccount(context)
            remove(item)
            add(0, item)
        }
    }

    data class PosDur(
        @JsonProperty("position") val position: Long,
        @JsonProperty("duration") val duration: Long
    )

    fun PosDur.fixVisual(): PosDur {
        if (duration <= 0) return PosDur(0, duration)
        val percentage = position * 100 / duration
        if (percentage <= 1) return PosDur(0, duration)
        if (percentage <= 5) return PosDur(5 * duration / 100, duration)
        if (percentage >= 95) return PosDur(duration, duration)
        return this
    }

    /**
     * Used to display notifications on new episodes and posters in library.
     **/
    abstract class LibrarySearchResponse(
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("latestUpdatedTime") open val latestUpdatedTime: Long,
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType?,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("year") open val year: Int?,
        @JsonProperty("syncData") open val syncData: Map<String, String>?,
        @JsonProperty("quality") override var quality: SearchQuality?,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>?,
        @JsonProperty("plot") open val plot : String? = null,
        @JsonProperty("rating") open val rating : Int? = null,
        @JsonProperty("tags") open val tags : List<String>? = null,
    ) : SearchResponse

    data class SubscribedData(
        @JsonProperty("subscribedTime") val subscribedTime: Long,
        @JsonProperty("lastSeenEpisodeCount") val lastSeenEpisodeCount: Map<DubStatus, Int?>,
        override var id: Int?,
        override val latestUpdatedTime: Long,
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override val year: Int?,
        override val syncData: Map<String, String>? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override val plot: String? = null,
        override val rating: Int? = null,
        override val tags: List<String>? = null,
    ) : LibrarySearchResponse(id, latestUpdatedTime, name, url, apiName, type, posterUrl, year, syncData, quality, posterHeaders, plot,rating,tags) {
        fun toLibraryItem(): SyncAPI.LibraryItem? {
            return SyncAPI.LibraryItem(
                name,
                url,
                id?.toString() ?: return null,
                null,
                null,
                null,
                latestUpdatedTime,
                apiName, type, posterUrl, posterHeaders, quality, this.id, plot = this.plot, rating = this.rating, tags = this.tags
            )
        }
    }

    data class BookmarkedData(
        @JsonProperty("bookmarkedTime") val bookmarkedTime: Long,
        override var id: Int?,
        override val latestUpdatedTime: Long,
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override val year: Int?,
        override val syncData: Map<String, String>? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override val plot: String? = null,
        override val rating: Int? = null,
        override val tags: List<String>? = null,
    ) : LibrarySearchResponse(id, latestUpdatedTime, name, url, apiName, type, posterUrl, year, syncData, quality, posterHeaders, plot) {
        fun toLibraryItem(id: String): SyncAPI.LibraryItem {
            return SyncAPI.LibraryItem(
                name,
                url,
                id,
                null,
                null,
                null,
                latestUpdatedTime,
                apiName, type, posterUrl, posterHeaders, quality, this.id, plot = this.plot, rating = this.rating, tags = this.tags
            )
        }
    }

    data class FavoritesData(
        @JsonProperty("favoritesTime") val favoritesTime: Long,
        override var id: Int?,
        override val latestUpdatedTime: Long,
        override val name: String,
        override val url: String,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override val year: Int?,
        override val syncData: Map<String, String>? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override val plot: String? = null,
        override val rating: Int? = null,
        override val tags: List<String>? = null,
    ) : LibrarySearchResponse(id, latestUpdatedTime, name, url, apiName, type, posterUrl, year, syncData, quality, posterHeaders,plot) {
        fun toLibraryItem(): SyncAPI.LibraryItem? {
            return SyncAPI.LibraryItem(
                name,
                url,
                id?.toString() ?: return null,
                null,
                null,
                null,
                latestUpdatedTime,
                apiName, type, posterUrl, posterHeaders, quality, this.id, plot = this.plot, rating = this.rating, tags = this.tags
            )
        }
    }

    data class ResumeWatchingResult(
        @JsonProperty("name") override val name: String,
        @JsonProperty("url") override val url: String,
        @JsonProperty("apiName") override val apiName: String,
        @JsonProperty("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") override var posterUrl: String?,
        @JsonProperty("watchPos") val watchPos: PosDur?,
        @JsonProperty("id") override var id: Int?,
        @JsonProperty("parentId") val parentId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
        @JsonProperty("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    ) : SearchResponse

    /**
     * A datastore wide account for future implementations of a multiple account system
     **/

    fun getAllWatchStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_WATCH_STATE"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun deleteAllResumeStateIds() {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING"
        removeKeys(folder)
    }

    fun deleteBookmarkedData(id: Int?) {
        if (id == null) return
        AccountManager.localListApi.requireLibraryRefresh = true
        removeKey("$currentAccount/$RESULT_WATCH_STATE", id.toString())
        removeKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun getAllResumeStateIds(): List<Int>? {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    private fun getAllResumeStateIdsOld(): List<Int>? {
        val folder = "$currentAccount/$RESULT_RESUME_WATCHING_OLD"
        return getKeys(folder)?.mapNotNull {
            it.removePrefix("$folder/").toIntOrNull()
        }
    }

    fun migrateResumeWatching() {
        // if (getKey(RESULT_RESUME_WATCHING_HAS_MIGRATED, false) != true) {
        setKey(RESULT_RESUME_WATCHING_HAS_MIGRATED, true)
        getAllResumeStateIdsOld()?.forEach { id ->
            getLastWatchedOld(id)?.let {
                setLastWatched(
                    it.parentId,
                    null,
                    it.episode,
                    it.season,
                    it.isFromDownload,
                    it.updateTime
                )
                removeLastWatchedOld(it.parentId)
            }
        }
        //}
    }

    fun setLastWatched(
        parentId: Int?,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean = false,
        updateTime: Long? = null,
    ) {
        if (parentId == null) return
        setKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            parentId.toString(),
            VideoDownloadHelper.ResumeWatching(
                parentId,
                episodeId,
                episode,
                season,
                updateTime ?: System.currentTimeMillis(),
                isFromDownload
            )
        )
    }

    private fun removeLastWatchedOld(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING_OLD", parentId.toString())
    }

    fun removeLastWatched(parentId: Int?) {
        if (parentId == null) return
        removeKey("$currentAccount/$RESULT_RESUME_WATCHING", parentId.toString())
    }

    fun getLastWatched(id: Int?): VideoDownloadHelper.ResumeWatching? {
        if (id == null) return null
        return getKey(
            "$currentAccount/$RESULT_RESUME_WATCHING",
            id.toString(),
        )
    }

    private fun getLastWatchedOld(id: Int?): VideoDownloadHelper.ResumeWatching? {
        if (id == null) return null
        return getKey(
            "$currentAccount/$RESULT_RESUME_WATCHING_OLD",
            id.toString(),
        )
    }

    fun setBookmarkedData(id: Int?, data: BookmarkedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString(), data)
        AccountManager.localListApi.requireLibraryRefresh = true
    }

    fun getBookmarkedData(id: Int?): BookmarkedData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id.toString())
    }

    fun getAllBookmarkedData(): List<BookmarkedData> {
        return getKeys("$currentAccount/$RESULT_WATCH_STATE_DATA")?.mapNotNull {
            getKey(it)
        } ?: emptyList()
    }

    fun getAllSubscriptions(): List<SubscribedData> {
        return getKeys("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA")?.mapNotNull {
            getKey(it)
        } ?: emptyList()
    }

    fun removeSubscribedData(id: Int?) {
        if (id == null) return
        AccountManager.localListApi.requireLibraryRefresh = true
        removeKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    }

    /**
     * Set new seen episodes and update time
     **/
    fun updateSubscribedData(id: Int?, data: SubscribedData?, episodeResponse: EpisodeResponse?) {
        if (id == null || data == null || episodeResponse == null) return
        val newData = data.copy(
            latestUpdatedTime = unixTimeMS,
            lastSeenEpisodeCount = episodeResponse.getLatestEpisodes()
        )
        setKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString(), newData)
    }

    fun setSubscribedData(id: Int?, data: SubscribedData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString(), data)
        AccountManager.localListApi.requireLibraryRefresh = true
    }

    fun getSubscribedData(id: Int?): SubscribedData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_SUBSCRIBED_STATE_DATA", id.toString())
    }

    fun getAllFavorites(): List<FavoritesData> {
        return getKeys("$currentAccount/$RESULT_FAVORITES_STATE_DATA")?.mapNotNull {
            getKey(it)
        } ?: emptyList()
    }

    fun removeFavoritesData(id: Int?) {
        if (id == null) return
        AccountManager.localListApi.requireLibraryRefresh = true
        removeKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString())
    }

    fun setFavoritesData(id: Int?, data: FavoritesData) {
        if (id == null) return
        setKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString(), data)
        AccountManager.localListApi.requireLibraryRefresh = true
    }

    fun getFavoritesData(id: Int?): FavoritesData? {
        if (id == null) return null
        return getKey("$currentAccount/$RESULT_FAVORITES_STATE_DATA", id.toString())
    }

    fun setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null) return
        if (dur < 30_000) return // too short
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    fun getViewPos(id: Int?): PosDur? {
        if (id == null) return null
        return getKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }

    fun getVideoWatchState(id: Int?): VideoWatchState? {
        if (id == null) return null
        return getKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), null)
    }

    fun setVideoWatchState(id: Int?, watchState: VideoWatchState) {
        if (id == null) return

        // None == No key
        if (watchState == VideoWatchState.None) {
            removeKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString())
        } else {
            setKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), watchState)
        }
    }

    fun getDub(id: Int): DubStatus? {
        return DubStatus.values()
            .getOrNull(getKey("$currentAccount/$RESULT_DUB", id.toString(), -1) ?: -1)
    }

    fun setDub(id: Int, status: DubStatus) {
        setKey("$currentAccount/$RESULT_DUB", id.toString(), status.ordinal)
    }

    fun setResultWatchState(id: Int?, status: Int) {
        if (id == null) return
        if (status == WatchType.NONE.internalId) {
            deleteBookmarkedData(id)
        } else {
            setKey("$currentAccount/$RESULT_WATCH_STATE", id.toString(), status)
        }
    }

    fun getResultWatchState(id: Int): WatchType {
        return WatchType.fromInternalId(
            getKey<Int>(
                "$currentAccount/$RESULT_WATCH_STATE",
                id.toString(),
                null
            )
        )
    }

    fun getResultSeason(id: Int): Int? {
        return getKey("$currentAccount/$RESULT_SEASON", id.toString(), null)
    }

    fun setResultSeason(id: Int, value: Int?) {
        setKey("$currentAccount/$RESULT_SEASON", id.toString(), value)
    }

    fun getResultEpisode(id: Int): Int? {
        return getKey("$currentAccount/$RESULT_EPISODE", id.toString(), null)
    }

    fun setResultEpisode(id: Int, value: Int?) {
        setKey("$currentAccount/$RESULT_EPISODE", id.toString(), value)
    }

    fun addSync(id: Int, idPrefix: String, url: String) {
        setKey("${idPrefix}_sync", id.toString(), url)
    }

    fun getSync(id: Int, idPrefixes: List<String>): List<String?> {
        return idPrefixes.map { idPrefix ->
            getKey("${idPrefix}_sync", id.toString())
        }
    }
}