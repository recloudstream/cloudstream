package com.lagradost.cloudstream3.ui.result

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getId
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.getCastSession
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.providers.Kitsu
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_NAVIGATE_TO
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.IGenerator
import com.lagradost.cloudstream3.ui.player.LoadType
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter.Companion.getPlayerAction
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.isAppInstalled
import com.lagradost.cloudstream3.utils.AppUtils.isConnectedToChromecast
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.Coroutines.ioWorkSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper.deleteBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllFavorites
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllSubscriptions
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDub
import com.lagradost.cloudstream3.utils.DataStoreHelper.getFavoritesData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getSubscribedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeFavoritesData
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeSubscribedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setDub
import com.lagradost.cloudstream3.utils.DataStoreHelper.setFavoritesData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultSeason
import com.lagradost.cloudstream3.utils.DataStoreHelper.setResultWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.setSubscribedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.setVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.updateSubscribedData
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit


/** This starts at 1 */
data class EpisodeRange(
    // used to index data
    val startIndex: Int,
    val length: Int,
    // used to display data
    val startEpisode: Int,
    val endEpisode: Int,
)

data class AutoResume(
    val season: Int?,
    val episode: Int?,
    val id: Int?,
    val startAction: Int,
)

data class ResultData(
    val url: String,
    val tags: List<String>,
    val actors: List<ActorData>?,
    val actorsText: UiText?,

    val comingSoon: Boolean,
    val backgroundPosterUrl: String?,
    val title: String,
    var syncData: Map<String, String>,

    val posterImage: UiImage?,
    val posterBackgroundImage: UiImage?,
    val plotText: UiText,
    val apiName: UiText,
    val ratingText: UiText?,
    val contentRatingText: UiText?,
    val vpnText: UiText?,
    val metaText: UiText?,
    val durationText: UiText?,
    val onGoingText: UiText?,
    val noEpisodesFoundText: UiText?,
    val titleText: UiText,
    val typeText: UiText,
    val yearText: UiText?,
    val nextAiringDate: UiText?,
    val nextAiringEpisode: UiText?,
    val plotHeaderText: UiText,
)

data class CheckDuplicateData(
    val name: String,
    val year: Int?,
    val syncData: Map<String, String>?
)

enum class LibraryListType {
    BOOKMARKS,
    FAVORITES,
    SUBSCRIPTIONS
}

fun txt(status: DubStatus?): UiText? {
    return txt(
        when (status) {
            DubStatus.Dubbed -> R.string.app_dubbed_text
            DubStatus.Subbed -> R.string.app_subbed_text
            else -> null
        }
    )
}

fun LoadResponse.toResultData(repo: APIRepository): ResultData {
    debugAssert({ repo.name != apiName }) {
        "Api returned wrong apiName"
    }

    val hasActorImages = actors?.firstOrNull()?.actor?.image?.isNotBlank() == true

    var nextAiringEpisode: UiText? = null
    var nextAiringDate: UiText? = null

    if (this is EpisodeResponse) {
        val airing = this.nextAiring
        if (airing != null && airing.unixTime > unixTime) {
            val seconds = airing.unixTime - unixTime
            val days = TimeUnit.SECONDS.toDays(seconds)
            val hours: Long = TimeUnit.SECONDS.toHours(seconds) - days * 24
            val minute =
                TimeUnit.SECONDS.toMinutes(seconds) - TimeUnit.SECONDS.toHours(seconds) * 60
            nextAiringDate = when {
                days > 0 -> {
                    txt(
                        R.string.next_episode_time_day_format,
                        days,
                        hours,
                        minute
                    )
                }

                hours > 0 -> txt(
                    R.string.next_episode_time_hour_format,
                    hours,
                    minute
                )

                minute > 0 -> txt(
                    R.string.next_episode_time_min_format,
                    minute
                )

                else -> null
            }?.also {
                nextAiringEpisode = txt(R.string.next_episode_format, airing.episode)
            }
        }
    }
    val dur = duration
    return ResultData(
        syncData = syncData,
        plotHeaderText = txt(
            when (this.type) {
                TvType.Torrent -> R.string.torrent_plot
                else -> R.string.result_plot
            }
        ),
        nextAiringDate = nextAiringDate,
        nextAiringEpisode = nextAiringEpisode,
        posterImage = img(
            posterUrl, posterHeaders
        ) ?: img(R.drawable.default_cover),
        posterBackgroundImage = img(
            backgroundPosterUrl ?: posterUrl, posterHeaders
        ) ?: img(R.drawable.default_cover),
        titleText = txt(name),
        url = url,
        tags = tags ?: emptyList(),
        comingSoon = comingSoon,
        actors = if (hasActorImages) actors else null,
        actorsText = if (hasActorImages || actors.isNullOrEmpty()) null else txt(
            R.string.cast_format,
            actors?.joinToString { it.actor.name }),
        plotText =
        if (plot.isNullOrBlank()) txt(if (this is TorrentLoadResponse) R.string.torrent_no_plot else R.string.normal_no_plot) else txt(
            plot!!
        ),
        backgroundPosterUrl = backgroundPosterUrl,
        title = name,
        typeText = txt(
            when (type) {
                TvType.TvSeries -> R.string.tv_series_singular
                TvType.Anime -> R.string.anime_singular
                TvType.OVA -> R.string.ova_singular
                TvType.AnimeMovie -> R.string.movies_singular
                TvType.Cartoon -> R.string.cartoons_singular
                TvType.Documentary -> R.string.documentaries_singular
                TvType.Movie -> R.string.movies_singular
                TvType.Torrent -> R.string.torrent_singular
                TvType.AsianDrama -> R.string.asian_drama_singular
                TvType.Live -> R.string.live_singular
                TvType.Others -> R.string.other_singular
                TvType.NSFW -> R.string.nsfw_singular
            }
        ),
        yearText = txt(year?.toString()),
        apiName = txt(apiName),
        ratingText = rating?.div(1000f)
            ?.let { if (it <= 0.1f) null else txt(R.string.rating_format, it) },
        contentRatingText = txt(contentRating),
        vpnText = txt(
            when (repo.vpnStatus) {
                VPNStatus.None -> null
                VPNStatus.Torrent -> R.string.vpn_torrent
                VPNStatus.MightBeNeeded -> R.string.vpn_might_be_needed
            }
        ),
        metaText =
        if (repo.providerType == ProviderType.MetaProvider) txt(R.string.provider_info_meta) else null,
        durationText = if (dur == null || dur <= 0) null else txt(
            R.string.duration_format,
            dur
        ),
        onGoingText = if (this is EpisodeResponse) {
            txt(
                when (showStatus) {
                    ShowStatus.Ongoing -> R.string.status_ongoing
                    ShowStatus.Completed -> R.string.status_completed
                    else -> null
                }
            )
        } else null,
        noEpisodesFoundText =
        if ((this is TvSeriesLoadResponse && this.episodes.isEmpty()) || (this is AnimeLoadResponse && !this.episodes.any { it.value.isNotEmpty() })) txt(
            R.string.no_episodes_found
        ) else null
    )
}


data class LinkProgress(
    val linksLoaded: Int,
    val subsLoaded: Int,
)

data class ResumeProgress(
    val progress: Int,
    val maxProgress: Int,
    val progressLeft: UiText,
)

data class ResumeWatchingStatus(
    val progress: ResumeProgress?,
    val isMovie: Boolean,
    val result: ResultEpisode,
)

data class LinkLoadingResult(
    val links: List<ExtractorLink>,
    val subs: List<SubtitleData>,
)

sealed class SelectPopup {
    data class SelectText(
        val text: UiText,
        val options: List<UiText>,
        val callback: (Int?) -> Unit
    ) : SelectPopup()

    data class SelectArray(
        val text: UiText,
        val options: List<Pair<UiText, Int>>,
        val callback: (Int?) -> Unit
    ) : SelectPopup()
}

fun SelectPopup.callback(index: Int?) {
    val ret = transformResult(index)
    return when (this) {
        is SelectPopup.SelectArray -> callback(ret)
        is SelectPopup.SelectText -> callback(ret)
    }
}

fun SelectPopup.transformResult(input: Int?): Int? {
    if (input == null) return null
    return when (this) {
        is SelectPopup.SelectArray -> options.getOrNull(input)?.second
        is SelectPopup.SelectText -> input
    }
}

fun SelectPopup.getTitle(context: Context): String {
    return when (this) {
        is SelectPopup.SelectArray -> text.asString(context)
        is SelectPopup.SelectText -> text.asString(context)
    }
}

fun SelectPopup.getOptions(context: Context): List<String> {
    return when (this) {
        is SelectPopup.SelectArray -> {
            this.options.map { it.first.asString(context) }
        }

        is SelectPopup.SelectText -> options.map { it.asString(context) }
    }
}

data class ExtractedTrailerData(
    var mirros: List<ExtractorLink>,
    var subtitles: List<SubtitleFile> = emptyList(),
)

class ResultViewModel2 : ViewModel() {
    private var currentResponse: LoadResponse? = null
    var EPISODE_RANGE_SIZE: Int = 20
    fun clear() {
        currentResponse = null
        _page.postValue(null)
    }

    data class EpisodeIndexer(
        val dubStatus: DubStatus,
        val season: Int,
    )

    /** map<dub, map<season, List<episode>>> */
    private var currentEpisodes: Map<EpisodeIndexer, List<ResultEpisode>> = mapOf()
    private var currentRanges: Map<EpisodeIndexer, List<EpisodeRange>> = mapOf()
    private var currentSeasons: List<Int> = listOf()
    private var currentDubStatus: List<DubStatus> = listOf()
    private var currentMeta: SyncAPI.SyncResult? = null
    private var currentSync: Map<String, String>? = null
    private var currentIndex: EpisodeIndexer? = null
    private var currentRange: EpisodeRange? = null
    private var currentShowFillers: Boolean = false
    var currentRepo: APIRepository? = null
    private var currentId: Int? = null
    private var fillers: Map<Int, Boolean> = emptyMap()
    private var generator: IGenerator? = null
    private var preferDubStatus: DubStatus? = null
    private var preferStartEpisode: Int? = null
    private var preferStartSeason: Int? = null
    //private val currentIsMovie get() = currentResponse?.isEpisodeBased() == false
    //private val currentHeaderName get() = currentResponse?.name


    private val _page: MutableLiveData<Resource<ResultData>?> =
        MutableLiveData(null)
    val page: LiveData<Resource<ResultData>?> = _page

    private val _episodes: MutableLiveData<Resource<List<ResultEpisode>>?> =
        MutableLiveData(Resource.Loading())
    val episodes: LiveData<Resource<List<ResultEpisode>>?> = _episodes

    private val _movie: MutableLiveData<Resource<Pair<UiText, ResultEpisode>>?> =
        MutableLiveData(null)
    val movie: LiveData<Resource<Pair<UiText, ResultEpisode>>?> = _movie

    private val _episodesCountText: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val episodesCountText: LiveData<UiText?> = _episodesCountText

    private val _trailers: MutableLiveData<List<ExtractedTrailerData>> =
        MutableLiveData(mutableListOf())
    val trailers: LiveData<List<ExtractedTrailerData>> = _trailers

    private val _dubSubSelections: MutableLiveData<List<Pair<UiText?, DubStatus>>> =
        MutableLiveData(emptyList())
    val dubSubSelections: LiveData<List<Pair<UiText?, DubStatus>>> = _dubSubSelections

    private val _rangeSelections: MutableLiveData<List<Pair<UiText?, EpisodeRange>>> =
        MutableLiveData(emptyList())
    val rangeSelections: LiveData<List<Pair<UiText?, EpisodeRange>>> = _rangeSelections

    private val _seasonSelections: MutableLiveData<List<Pair<UiText?, Int>>> =
        MutableLiveData(emptyList())
    val seasonSelections: LiveData<List<Pair<UiText?, Int>>> = _seasonSelections

    private val _recommendations: MutableLiveData<List<SearchResponse>> =
        MutableLiveData(emptyList())
    val recommendations: LiveData<List<SearchResponse>> = _recommendations

    private val _selectedRange: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val selectedRange: LiveData<UiText?> = _selectedRange

    private val _selectedSeason: MutableLiveData<UiText?> =
        MutableLiveData(null)
    val selectedSeason: LiveData<UiText?> = _selectedSeason

    private val _selectedDubStatus: MutableLiveData<UiText?> = MutableLiveData(null)
    val selectedDubStatus: LiveData<UiText?> = _selectedDubStatus

    private val _selectedRangeIndex: MutableLiveData<Int> =
        MutableLiveData(-1)
    val selectedRangeIndex: LiveData<Int> = _selectedRangeIndex

    private val _selectedSeasonIndex: MutableLiveData<Int> =
        MutableLiveData(-1)
    val selectedSeasonIndex: LiveData<Int> = _selectedSeasonIndex

    private val _selectedDubStatusIndex: MutableLiveData<Int> = MutableLiveData(-1)
    val selectedDubStatusIndex: LiveData<Int> = _selectedDubStatusIndex

    private val _loadedLinks: MutableLiveData<LinkProgress?> = MutableLiveData(null)
    val loadedLinks: LiveData<LinkProgress?> = _loadedLinks

    private val _resumeWatching: MutableLiveData<ResumeWatchingStatus?> =
        MutableLiveData(null)
    val resumeWatching: LiveData<ResumeWatchingStatus?> = _resumeWatching

    private val _episodeSynopsis: MutableLiveData<String?> = MutableLiveData(null)
    val episodeSynopsis: LiveData<String?> = _episodeSynopsis

    private val _subscribeStatus: MutableLiveData<Boolean?> = MutableLiveData(null)
    val subscribeStatus: LiveData<Boolean?> = _subscribeStatus

    private val _favoriteStatus: MutableLiveData<Boolean?> = MutableLiveData(null)
    val favoriteStatus: LiveData<Boolean?> = _favoriteStatus

    companion object {
        const val TAG = "RVM2"
        //private const val EPISODE_RANGE_SIZE = 20
        //private const val EPISODE_RANGE_OVERLOAD = 30

        private fun List<SeasonData>?.getSeason(season: Int?): SeasonData? {
            if (season == null) return null
            return this?.firstOrNull { it.season == season }
        }

        private fun filterName(name: String?): String? {
            if (name == null) return null
            Regex("[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)?.let {
                if (it.isEmpty())
                    return null
            }
            return name
        }

        fun singleMap(ep: ResultEpisode): Map<EpisodeIndexer, List<ResultEpisode>> =
            mapOf(
                EpisodeIndexer(DubStatus.None, 0) to listOf(
                    ep
                )
            )

        private fun getRanges(
            allEpisodes: Map<EpisodeIndexer, List<ResultEpisode>>,
            EPISODE_RANGE_SIZE: Int
        ): Map<EpisodeIndexer, List<EpisodeRange>> {
            return allEpisodes.keys.mapNotNull { index ->
                val episodes =
                    allEpisodes[index] ?: return@mapNotNull null // this should never happened

                // fast case
                val EPISODE_RANGE_OVERLOAD = EPISODE_RANGE_SIZE + 10
                if (episodes.size <= EPISODE_RANGE_OVERLOAD) {
                    return@mapNotNull index to listOf(
                        EpisodeRange(
                            0,
                            episodes.size,
                            episodes.minOf { it.episode },
                            episodes.maxOf { it.episode })
                    )
                }

                if (episodes.isEmpty()) {
                    return@mapNotNull null
                }

                val list = mutableListOf<EpisodeRange>()

                val currentEpisode = episodes.first()
                var currentIndex = 0
                val maxIndex = episodes.size
                var targetEpisode = 0
                var currentMin = currentEpisode.episode
                var currentMax = currentEpisode.episode

                while (currentIndex < maxIndex) {
                    val startIndex = currentIndex
                    targetEpisode += EPISODE_RANGE_SIZE
                    while (currentIndex < maxIndex && episodes[currentIndex].episode <= targetEpisode) {
                        val episodeNumber = episodes[currentIndex].episode
                        if (episodeNumber < currentMin) {
                            currentMin = episodeNumber
                        }
                        if (episodeNumber > currentMax) {
                            currentMax = episodeNumber
                        }
                        ++currentIndex
                    }

                    val length = currentIndex - startIndex
                    if (length <= 0) continue
                    list.add(
                        EpisodeRange(
                            startIndex,
                            length,
                            currentMin,
                            currentMax
                        )
                    )
                    currentMin = Int.MAX_VALUE
                    currentMax = Int.MIN_VALUE
                }

                /*var currentMin = Int.MAX_VALUE
                var currentMax = Int.MIN_VALUE
                var currentStartIndex = 0
                var currentLength = 0
                for (ep in episodes) {
                    val episodeNumber = ep.episode
                    if (episodeNumber < currentMin) {
                        currentMin = episodeNumber
                    } else if (episodeNumber > currentMax) {
                        currentMax = episodeNumber
                    }

                    if (++currentLength >= EPISODE_RANGE_SIZE) {
                        list.add(
                            EpisodeRange(
                                currentStartIndex,
                                currentLength,
                                currentMin,
                                currentMax
                            )
                        )
                        currentMin = Int.MAX_VALUE
                        currentMax = Int.MIN_VALUE
                        currentStartIndex += currentLength
                        currentLength = 0
                    }
                }
                if (currentLength > 0) {
                    list.add(
                        EpisodeRange(
                            currentStartIndex,
                            currentLength,
                            currentMin,
                            currentMax
                        )
                    )
                }*/

                index to list
            }.toMap()
        }

        private fun downloadSubtitle(
            context: Context?,
            link: ExtractorSubtitleLink,
            fileName: String,
            folder: String
        ) {
            ioSafe {
                VideoDownloadManager.downloadThing(
                    context ?: return@ioSafe,
                    link,
                    "$fileName ${link.name}",
                    folder,
                    if (link.url.contains(".srt")) "srt" else "vtt",
                    false,
                    null, createNotificationCallback = {}
                )
            }
        }

        private fun getFolder(currentType: TvType, titleName: String): String {
            val sanitizedFileName = VideoDownloadManager.sanitizeFilename(titleName)
            return when (currentType) {
                TvType.Anime -> "Anime/$sanitizedFileName"
                TvType.Movie -> "Movies"
                TvType.AnimeMovie -> "Movies"
                TvType.TvSeries -> "TVSeries/$sanitizedFileName"
                TvType.OVA -> "OVA"
                TvType.Cartoon -> "Cartoons/$sanitizedFileName"
                TvType.Torrent -> "Torrent"
                TvType.Documentary -> "Documentaries"
                TvType.AsianDrama -> "AsianDrama"
                TvType.Live -> "LiveStreams"
                TvType.NSFW -> "NSFW"
                TvType.Others -> "Others"
            }
        }

        private fun downloadSubtitle(
            context: Context?,
            link: SubtitleData,
            meta: VideoDownloadManager.DownloadEpisodeMetadata,
        ) {
            context?.let { ctx ->
                val fileName = VideoDownloadManager.getFileName(ctx, meta)
                val folder = getFolder(meta.type ?: return, meta.mainName)
                downloadSubtitle(
                    ctx,
                    ExtractorSubtitleLink(link.name, link.url, ""),
                    fileName,
                    folder
                )
            }
        }

        fun startDownload(
            context: Context?,
            episode: ResultEpisode,
            currentIsMovie: Boolean,
            currentHeaderName: String,
            currentType: TvType,
            currentPoster: String?,
            apiName: String,
            parentId: Int,
            url: String,
            links: List<ExtractorLink>,
            subs: List<SubtitleData>?
        ) {
            try {
                if (context == null) return

                val meta =
                    getMeta(
                        episode,
                        currentHeaderName,
                        apiName,
                        currentPoster,
                        currentIsMovie,
                        currentType
                    )

                val folder = getFolder(currentType, currentHeaderName)

                val src = "$DOWNLOAD_NAVIGATE_TO/$parentId" // url ?: return@let

                // SET VISUAL KEYS
                setKey(
                    DOWNLOAD_HEADER_CACHE,
                    parentId.toString(),
                    VideoDownloadHelper.DownloadHeaderCached(
                        apiName,
                        url,
                        currentType,
                        currentHeaderName,
                        currentPoster,
                        parentId,
                        System.currentTimeMillis(),
                    )
                )

                setKey(
                    DataStore.getFolderName(
                        DOWNLOAD_EPISODE_CACHE,
                        parentId.toString()
                    ), // 3 deep folder for faster acess
                    episode.id.toString(),
                    VideoDownloadHelper.DownloadEpisodeCached(
                        episode.name,
                        episode.poster,
                        episode.episode,
                        episode.season,
                        episode.id,
                        parentId,
                        episode.rating,
                        episode.description,
                        System.currentTimeMillis(),
                    )
                )

                // DOWNLOAD VIDEO
                VideoDownloadManager.downloadEpisodeUsingWorker(
                    context,
                    src,//url ?: return,
                    folder,
                    meta,
                    links
                )

                // 1. Checks if the lang should be downloaded
                // 2. Makes it into the download format
                // 3. Downloads it as a .vtt file
                val downloadList = SubtitlesFragment.getDownloadSubsLanguageISO639_1()
                subs?.let { subsList ->
                    subsList.filter {
                        downloadList.contains(
                            SubtitleHelper.fromLanguageToTwoLetters(
                                it.name,
                                true
                            )
                        )
                    }
                        .map { ExtractorSubtitleLink(it.name, it.url, "") }.take(3)
                        .forEach { link ->
                            val fileName = VideoDownloadManager.getFileName(context, meta)
                            downloadSubtitle(context, link, fileName, folder)
                        }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        suspend fun downloadEpisode(
            activity: Activity?,
            episode: ResultEpisode,
            currentIsMovie: Boolean,
            currentHeaderName: String,
            currentType: TvType,
            currentPoster: String?,
            apiName: String,
            parentId: Int,
            url: String,
        ) {
            ioSafe {
                val generator = RepoLinkGenerator(listOf(episode))
                val currentLinks = mutableSetOf<ExtractorLink>()
                val currentSubs = mutableSetOf<SubtitleData>()
                generator.generateLinks(clearCache = false, LoadType.Chromecast, callback = {
                    it.first?.let { link ->
                        currentLinks.add(link)
                    }
                }, subtitleCallback = { sub ->
                    currentSubs.add(sub)
                })

                if (currentLinks.isEmpty()) {
                    main {
                        showToast(
                            R.string.no_links_found_toast,
                            Toast.LENGTH_SHORT
                        )
                    }
                    return@ioSafe
                } else {
                    main {
                        showToast(
                            R.string.download_started,
                            Toast.LENGTH_SHORT
                        )
                    }
                }

                startDownload(
                    activity,
                    episode,
                    currentIsMovie,
                    currentHeaderName,
                    currentType,
                    currentPoster,
                    apiName,
                    parentId,
                    url,
                    sortUrls(currentLinks),
                    sortSubs(currentSubs),
                )
            }
        }

        private fun getMeta(
            episode: ResultEpisode,
            titleName: String,
            apiName: String,
            currentPoster: String?,
            currentIsMovie: Boolean,
            tvType: TvType,
        ): VideoDownloadManager.DownloadEpisodeMetadata {
            return VideoDownloadManager.DownloadEpisodeMetadata(
                episode.id,
                VideoDownloadManager.sanitizeFilename(titleName),
                apiName,
                episode.poster ?: currentPoster,
                episode.name,
                if (currentIsMovie) null else episode.season,
                if (currentIsMovie) null else episode.episode,
                tvType,
            )
        }
    }

    private val _watchStatus: MutableLiveData<WatchType> = MutableLiveData(WatchType.NONE)
    val watchStatus: LiveData<WatchType> get() = _watchStatus

    private val _selectPopup: MutableLiveData<SelectPopup?> = MutableLiveData(null)
    val selectPopup: LiveData<SelectPopup?> = _selectPopup

    fun updateWatchStatus(
        status: WatchType,
        context: Context?,
        loadResponse: LoadResponse? = null,
        statusChangedCallback: ((statusChanged: Boolean) -> Unit)? = null
    ) {
        val (response,currentId) = loadResponse?.let { load ->
            (load to load.getId())
        } ?: ((currentResponse ?: return) to (currentId ?: return))

        val currentStatus = getResultWatchState(currentId)

        // If the current status is "NONE" and the new status is not "NONE",
        // fetch the bookmarked data to check for duplicates, otherwise set this
        // to an empty list, so that we don't show the duplicate warning dialog,
        // but we still want to update the current bookmark and refresh the data anyway.
        val bookmarkedData = if (currentStatus == WatchType.NONE && status != WatchType.NONE) {
            getAllBookmarkedData()
        } else emptyList()

        checkAndWarnDuplicates(
            context,
            LibraryListType.BOOKMARKS,
            CheckDuplicateData(
                name = response.name,
                year = response.year,
                syncData = response.syncData,
            ),
            bookmarkedData
        ) { shouldContinue: Boolean, duplicateIds: List<Int?> ->
            if (!shouldContinue) return@checkAndWarnDuplicates

            if (duplicateIds.isNotEmpty()) {
                duplicateIds.forEach { duplicateId ->
                    deleteBookmarkedData(duplicateId)
                }
            }

            setResultWatchState(currentId, status.internalId)

            // We don't need to store if WatchType.NONE.
            // The key is removed in setResultWatchState, we don't want to
            // re-add it again here if it was just removed.
            if (status != WatchType.NONE) {
                val current = getBookmarkedData(currentId)

                setBookmarkedData(
                    currentId,
                    DataStoreHelper.BookmarkedData(
                        current?.bookmarkedTime ?: unixTimeMS,
                        currentId,
                        unixTimeMS,
                        response.name,
                        response.url,
                        response.apiName,
                        response.type,
                        response.posterUrl,
                        response.year,
                        response.syncData,
                        plot = response.plot,
                        tags = response.tags,
                        rating = response.rating
                    )
                )
            }

            if (currentStatus != status) {
                MainActivity.bookmarksUpdatedEvent(true)
                MainActivity.reloadLibraryEvent(true)
            }

            _watchStatus.postValue(status)

            statusChangedCallback?.invoke(true)
        }
    }

    private fun startChromecast(
        activity: Activity?,
        result: ResultEpisode,
        isVisible: Boolean = true
    ) {
        if (activity == null) return
        loadLinks(result, isVisible = isVisible, LoadType.Chromecast) { data ->
            startChromecast(activity, result, data.links, data.subs, 0)
        }
    }

    /**
     * Toggles the subscription status of an item.
     *
     * @param context The context to use for operations.
     * @param statusChangedCallback A callback that is invoked when the subscription status changes.
     *        It provides the new subscription status (true if subscribed, false if unsubscribed, null if action was canceled).
     */
    fun toggleSubscriptionStatus(
        context: Context?,
        statusChangedCallback: ((newStatus: Boolean?) -> Unit)? = null
    ) {
        val isSubscribed = _subscribeStatus.value ?: return
        val response = currentResponse ?: return
        if (response !is EpisodeResponse) return

        val currentId = currentId ?: return

        if (isSubscribed) {
            removeSubscribedData(currentId)
            statusChangedCallback?.invoke(false)
            _subscribeStatus.postValue(false)
        } else {
            checkAndWarnDuplicates(
                context,
                LibraryListType.SUBSCRIPTIONS,
                CheckDuplicateData(
                    name = response.name,
                    year = response.year,
                    syncData = response.syncData,
                ),
                getAllSubscriptions(),
            ) { shouldContinue: Boolean, duplicateIds: List<Int?> ->
                if (!shouldContinue) {
                    statusChangedCallback?.invoke(null)
                    return@checkAndWarnDuplicates
                }

                if (duplicateIds.isNotEmpty()) {
                    duplicateIds.forEach { duplicateId ->
                        removeSubscribedData(duplicateId)
                    }
                }

                val current = getSubscribedData(currentId)

                setSubscribedData(
                    currentId,
                    DataStoreHelper.SubscribedData(
                        current?.subscribedTime ?: unixTimeMS,
                        response.getLatestEpisodes(),
                        currentId,
                        unixTimeMS,
                        response.name,
                        response.url,
                        response.apiName,
                        response.type,
                        response.posterUrl,
                        response.year,
                        response.syncData,
                        plot = response.plot,
                        rating = response.rating,
                        tags = response.tags
                    )
                )

                _subscribeStatus.postValue(true)

                statusChangedCallback?.invoke(true)
            }
        }
    }

    /**
     * Toggles the favorite status of an item.
     *
     * @param context The context to use.
     * @param statusChangedCallback A callback that is invoked when the favorite status changes.
     *        It provides the new favorite status (true if added to favorites, false if removed, null if action was canceled).
     */
    fun toggleFavoriteStatus(
        context: Context?,
        statusChangedCallback: ((newStatus: Boolean?) -> Unit)? = null
    ) {
        val isFavorite = _favoriteStatus.value ?: return
        val response = currentResponse ?: return

        val currentId = currentId ?: return

        if (isFavorite) {
            removeFavoritesData(currentId)
            statusChangedCallback?.invoke(false)
            _favoriteStatus.postValue(false)
        } else {
            checkAndWarnDuplicates(
                context,
                LibraryListType.FAVORITES,
                CheckDuplicateData(
                    name = response.name,
                    year = response.year,
                    syncData = response.syncData,
                ),
                getAllFavorites(),
            ) { shouldContinue: Boolean, duplicateIds: List<Int?> ->
                if (!shouldContinue) {
                    statusChangedCallback?.invoke(null)
                    return@checkAndWarnDuplicates
                }

                if (duplicateIds.isNotEmpty()) {
                    duplicateIds.forEach { duplicateId ->
                        removeFavoritesData(duplicateId)
                    }
                }

                val current = getFavoritesData(currentId)

                setFavoritesData(
                    currentId,
                    DataStoreHelper.FavoritesData(
                        current?.favoritesTime ?: unixTimeMS,
                        currentId,
                        unixTimeMS,
                        response.name,
                        response.url,
                        response.apiName,
                        response.type,
                        response.posterUrl,
                        response.year,
                        response.syncData,
                        plot = response.plot,
                        rating = response.rating,
                        tags = response.tags
                    )
                )

                _favoriteStatus.postValue(true)

                statusChangedCallback?.invoke(true)
            }
        }
    }

    @MainThread
    private fun checkAndWarnDuplicates(
        context: Context?,
        listType: LibraryListType,
        checkDuplicateData: CheckDuplicateData,
        data: List<DataStoreHelper.LibrarySearchResponse>,
        checkDuplicatesCallback: (shouldContinue: Boolean, duplicateIds: List<Int?>) -> Unit
    ) {
        val whitespaceRegex = "\\s+".toRegex()
        fun normalizeString(input: String): String {
            /**
             * Trim the input string and replace consecutive spaces with a single space.
             * This covers some edge-cases where the title does not match exactly across providers,
             * and one provider has the title with an extra whitespace. This is minor enough that
             * it should still match in this case.
             */
            return input.trim().replace(whitespaceRegex, " ")
        }

        val syncData = checkDuplicateData.syncData

        val imdbId = getImdbIdFromSyncData(syncData)
        val tmdbId = getTMDbIdFromSyncData(syncData)
        val malId = syncData?.get(AccountManager.malApi.idPrefix)
        val aniListId = syncData?.get(AccountManager.aniListApi.idPrefix)
        val normalizedName = normalizeString(checkDuplicateData.name)
        val year = checkDuplicateData.year

        val duplicateEntries = data.filter { it: DataStoreHelper.LibrarySearchResponse ->
            val librarySyncData = it.syncData

            val checks = listOf(
                { imdbId != null && getImdbIdFromSyncData(librarySyncData) == imdbId },
                { tmdbId != null && getTMDbIdFromSyncData(librarySyncData) == tmdbId },
                { malId != null && librarySyncData?.get(AccountManager.malApi.idPrefix) == malId },
                { aniListId != null && librarySyncData?.get(AccountManager.aniListApi.idPrefix) == aniListId },
                { normalizedName == normalizeString(it.name) && year == it.year }
            )

            checks.any { it() }
        }

        if (duplicateEntries.isEmpty() || context == null) {
            checkDuplicatesCallback.invoke(true, emptyList())
            return
        }

        val replaceMessage = if (duplicateEntries.size > 1) {
            R.string.duplicate_replace_all
        } else R.string.duplicate_replace

        val message = if (duplicateEntries.size == 1) {
            val list = when (listType) {
                LibraryListType.BOOKMARKS -> getResultWatchState(duplicateEntries[0].id ?: 0).stringRes
                LibraryListType.FAVORITES -> R.string.favorites_list_name
                LibraryListType.SUBSCRIPTIONS -> R.string.subscription_list_name
            }

            context.getString(R.string.duplicate_message_single,
                "${normalizeString(duplicateEntries[0].name)} (${context.getString(list)}) — ${duplicateEntries[0].apiName}"
            )
        } else {
            val bulletPoints = duplicateEntries.joinToString("\n") {
                val list = when (listType) {
                    LibraryListType.BOOKMARKS -> getResultWatchState(it.id ?: 0).stringRes
                    LibraryListType.FAVORITES -> R.string.favorites_list_name
                    LibraryListType.SUBSCRIPTIONS -> R.string.subscription_list_name
                }

                "• ${it.apiName}: ${normalizeString(it.name)} (${context.getString(list)})"
            }

            context.getString(R.string.duplicate_message_multiple, bulletPoints)
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        checkDuplicatesCallback.invoke(true, emptyList())
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        checkDuplicatesCallback.invoke(false, emptyList())
                    }
                    DialogInterface.BUTTON_NEUTRAL -> {
                        checkDuplicatesCallback.invoke(true, duplicateEntries.map { it.id })
                    }
                }
            }

        builder.setTitle(R.string.duplicate_title)
            .setMessage(message)
            .setPositiveButton(R.string.duplicate_add, dialogClickListener)
            .setNegativeButton(R.string.duplicate_cancel, dialogClickListener)
            .setNeutralButton(replaceMessage, dialogClickListener)
            .show().setDefaultFocus()
    }

    private fun getImdbIdFromSyncData(syncData: Map<String, String>?): String? {
        return normalSafeApiCall {
            SimklApi.readIdFromString(
                syncData?.get(AccountManager.simklApi.idPrefix)
            )[SimklApi.Companion.SyncServices.Imdb]
        }
    }

    private fun getTMDbIdFromSyncData(syncData: Map<String, String>?): String? {
        return normalSafeApiCall {
            SimklApi.readIdFromString(
                syncData?.get(AccountManager.simklApi.idPrefix)
            )[SimklApi.Companion.SyncServices.Tmdb]
        }
    }

    private fun startChromecast(
        activity: Activity?,
        result: ResultEpisode,
        links: List<ExtractorLink>,
        subs: List<SubtitleData>,
        startIndex: Int,
    ) {
        if (activity == null) return
        val response = currentResponse ?: return
        val eps = currentEpisodes[currentIndex ?: return] ?: return

        // Main needed because getCastSession needs to be on main thread
        main {
            activity.getCastSession()?.startCast(
                response.apiName,
                response.isMovie(),
                response.name,
                response.posterUrl,
                result.index,
                eps,
                links,
                subs,
                startTime = result.getRealPosition(),
                startIndex = startIndex
            )
        }
    }

    fun cancelLinks() {
        currentLoadLinkJob?.cancel()
        currentLoadLinkJob = null
        _loadedLinks.postValue(null)
    }

    private fun postPopup(text: UiText, options: List<UiText>, callback: suspend (Int?) -> Unit) {
        _selectPopup.postValue(
            SelectPopup.SelectText(
                text,
                options
            ) { value ->
                viewModelScope.launchSafe {
                    _selectPopup.postValue(null)
                    callback.invoke(value)
                }
            }
        )
    }

    @JvmName("postPopupArray")
    private fun postPopup(
        text: UiText,
        options: List<Pair<UiText, Int>>,
        callback: suspend (Int?) -> Unit
    ) {
        _selectPopup.postValue(
            SelectPopup.SelectArray(
                text,
                options,
            ) { value ->
                viewModelScope.launchSafe {
                    _selectPopup.postValue(null)
                    callback.invoke(value)
                }
            }
        )
    }

    private fun loadLinks(
        result: ResultEpisode,
        isVisible: Boolean,
        type: LoadType,
        clearCache: Boolean = false,
        work: suspend (CoroutineScope.(LinkLoadingResult) -> Unit)
    ) {
        currentLoadLinkJob?.cancel()
        currentLoadLinkJob = ioSafe {
            val links = loadLinks(
                result,
                isVisible = isVisible,
                type = type,
                clearCache = clearCache
            )
            if (!this.isActive) return@ioSafe
            work(links)
        }
    }

    private var currentLoadLinkJob: Job? = null
    private fun acquireSingleLink(
        result: ResultEpisode,
        type: LoadType,
        text: UiText,
        callback: (Pair<LinkLoadingResult, Int>) -> Unit,
    ) {
        loadLinks(result, isVisible = true, type) { links ->
            postPopup(
                text,
                links.links.map { txt("${it.name} ${Qualities.getStringByInt(it.quality)}") }) {
                callback.invoke(links to (it ?: return@postPopup))
            }
        }
    }

    private fun acquireSingleSubtitle(
        result: ResultEpisode,
        text: UiText,
        callback: (Pair<LinkLoadingResult, Int>) -> Unit,
    ) {
        loadLinks(result, isVisible = true, type = LoadType.Unknown) { links ->
            postPopup(
                text,
                links.subs.map { txt(it.name) })
            {
                callback.invoke(links to (it ?: return@postPopup))
            }
        }
    }

    private suspend fun CoroutineScope.loadLinks(
        result: ResultEpisode,
        isVisible: Boolean,
        type: LoadType,
        clearCache: Boolean = false,
    ): LinkLoadingResult {
        val tempGenerator = RepoLinkGenerator(listOf(result))

        val links: MutableSet<ExtractorLink> = mutableSetOf()
        val subs: MutableSet<SubtitleData> = mutableSetOf()
        fun updatePage() {
            if (isVisible && isActive) {
                _loadedLinks.postValue(LinkProgress(links.size, subs.size))
            }
        }
        try {
            updatePage()
            tempGenerator.generateLinks(clearCache, type, { (link, _) ->
                if (link != null) {
                    links += link
                    updatePage()
                }
            }, { sub ->
                subs += sub
                updatePage()
            })
        } catch (e: Exception) {
            logError(e)
        } finally {
            _loadedLinks.postValue(null)
        }

        return LinkLoadingResult(sortUrls(links), sortSubs(subs))
    }

    private fun launchActivity(
        activity: Activity?,
        resumeApp: ResultResume,
        id: Int? = null,
        work: suspend (Intent.(Activity) -> Unit)
    ): Job? {
        val act = activity ?: return null
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                resumeApp.launch(id) {
                    work(act)
                }
            } catch (t: Throwable) {
                logError(t)
                main {
                    if (t is ActivityNotFoundException) {
                        showToast(txt(R.string.app_not_found_error), Toast.LENGTH_LONG)
                    } else {
                        showToast(t.toString(), Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    private fun playInWebVideo(
        activity: Activity?,
        link: ExtractorLink,
        title: String?,
        posterUrl: String?,
        subtitles: List<SubtitleData>
    ) = launchActivity(activity, WEB_VIDEO) {
        setDataAndType(Uri.parse(link.url), "video/*")

        putExtra("subs", subtitles.map { it.url.toUri() }.toTypedArray())
        title?.let { putExtra("title", title) }
        posterUrl?.let { putExtra("poster", posterUrl) }
        val headers = Bundle().apply {
            if (link.referer.isNotBlank())
                putString("Referer", link.referer)
            putString("User-Agent", USER_AGENT)
            for ((key, value) in link.headers) {
                putString(key, value)
            }
        }
        putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
        putExtra("secure_uri", true)
    }

    private fun playWithMpv(
        activity: Activity?,
        id: Int,
        link: ExtractorLink,
        subtitles: List<SubtitleData>,
        resume: Boolean = true,
    ) = launchActivity(activity, MPV, id) {
        putExtra("subs", subtitles.map { it.url.toUri() }.toTypedArray())
        putExtra("subs.name", subtitles.map { it.name }.toTypedArray())
        putExtra("subs.filename", subtitles.map { it.name }.toTypedArray())
        setDataAndType(Uri.parse(link.url), "video/*")
        component = MPV_COMPONENT
        putExtra("secure_uri", true)
        putExtra("return_result", true)
        val position = getViewPos(id)?.position
        if (resume && position != null)
            putExtra("position", position.toInt())
    }

    // https://wiki.videolan.org/Android_Player_Intents/
    private fun playWithVlc(
        activity: Activity?,
        data: LinkLoadingResult,
        id: Int,
        resume: Boolean = true,
        // if it is only a single link then resume works correctly
        singleFile: Boolean? = null
    ) = launchActivity(activity, VLC, id) { act ->
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val outputDir = act.cacheDir

        if (singleFile ?: (data.links.size == 1)) {
            setDataAndType(data.links.first().url.toUri(), "video/*")
        } else {
            val outputFile = File.createTempFile("mirrorlist", ".m3u8", outputDir)

            var text = "#EXTM3U"

            // With subtitles it doesn't work for no reason :(
//            for (sub in data.subs) {
//                text += "\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${sub.name}\",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,LANGUAGE=\"${sub.name}\",URI=\"${sub.url}\""
//            }
            for (link in data.links) {
                text += "\n#EXTINF:, ${link.name}\n${link.url}"
            }
            outputFile.writeText(text)

            setDataAndType(
                FileProvider.getUriForFile(
                    act,
                    act.applicationContext.packageName + ".provider",
                    outputFile
                ), "video/*"
            )
        }

        val position = if (resume) {
            getViewPos(id)?.position ?: 0L
        } else {
            1L
        }

        // Component no longer safe to use in A13 for VLC
        // https://code.videolan.org/videolan/vlc-android/-/issues/2776
        // This will likely need to be updated once VLC fixes their documentation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            component = VLC_COMPONENT
        }

        putExtra("from_start", !resume)
        putExtra("position", position)
    }


    fun handleAction(click: EpisodeClickEvent) =
        viewModelScope.launchSafe {
            handleEpisodeClickEvent(click)
        }

    data class ExternalApp(
        val packageString: String,
        val name: Int,
        val action: Int,
    )

    private val apps = listOf(
        ExternalApp(
            VLC_PACKAGE,
            R.string.player_settings_play_in_vlc,
            ACTION_PLAY_EPISODE_IN_VLC_PLAYER
        ), ExternalApp(
            WEB_VIDEO_CAST_PACKAGE,
            R.string.player_settings_play_in_web,
            ACTION_PLAY_EPISODE_IN_WEB_VIDEO
        ),
        ExternalApp(
            MPV_PACKAGE,
            R.string.player_settings_play_in_mpv,
            ACTION_PLAY_EPISODE_IN_MPV
        )
    )

    fun releaseEpisodeSynopsis() {
        _episodeSynopsis.postValue(null)
    }

    private suspend fun handleEpisodeClickEvent(click: EpisodeClickEvent) {
        when (click.action) {
            ACTION_SHOW_OPTIONS -> {
                val options = mutableListOf<Pair<UiText, Int>>()
                if (activity?.isConnectedToChromecast() == true) {
                    options.addAll(
                        listOf(
                            txt(R.string.episode_action_chromecast_episode) to ACTION_CHROME_CAST_EPISODE,
                            txt(R.string.episode_action_chromecast_mirror) to ACTION_CHROME_CAST_MIRROR,
                        )
                    )
                }
                options.add(txt(R.string.episode_action_play_in_app) to ACTION_PLAY_EPISODE_IN_PLAYER)

                for (app in apps) {
                    if (activity?.isAppInstalled(app.packageString) == true) {
                        options.add(
                            txt(
                                R.string.episode_action_play_in_format,
                                txt(app.name)
                            ) to app.action
                        )
                    }
                }

                options.addAll(
                    listOf(
                        txt(R.string.episode_action_play_in_browser) to ACTION_PLAY_EPISODE_IN_BROWSER,
                        txt(R.string.episode_action_copy_link) to ACTION_COPY_LINK,
                        txt(R.string.episode_action_auto_download) to ACTION_DOWNLOAD_EPISODE,
                        txt(R.string.episode_action_download_mirror) to ACTION_DOWNLOAD_MIRROR,
                        txt(R.string.episode_action_download_subtitle) to ACTION_DOWNLOAD_EPISODE_SUBTITLE_MIRROR,
                        txt(R.string.episode_action_reload_links) to ACTION_RELOAD_EPISODE,
                    )
                )

                // Do not add mark as watched on movies
                if (!listOf(TvType.Movie, TvType.AnimeMovie).contains(click.data.tvType)) {
                    val isWatched =
                        getVideoWatchState(click.data.id) == VideoWatchState.Watched

                    val watchedText = if (isWatched) R.string.action_remove_from_watched
                    else R.string.action_mark_as_watched

                    options.add(txt(watchedText) to ACTION_MARK_AS_WATCHED)
                }

                postPopup(
                    txt(
                        activity?.getNameFull(
                            click.data.name,
                            click.data.episode,
                            click.data.season
                        ) ?: ""
                    ), // TODO FIX
                    options
                ) { result ->
                    handleEpisodeClickEvent(
                        click.copy(action = result ?: return@postPopup)
                    )
                }
            }

            ACTION_CLICK_DEFAULT -> {
                activity?.let { ctx ->
                    if (ctx.isConnectedToChromecast()) {
                        handleEpisodeClickEvent(
                            click.copy(action = ACTION_CHROME_CAST_EPISODE)
                        )
                    } else {
                        val action = getPlayerAction(ctx)
                        handleEpisodeClickEvent(
                            click.copy(action = action)
                        )
                    }
                }
            }

            ACTION_SHOW_DESCRIPTION -> {
                _episodeSynopsis.postValue(click.data.description)
            }

            /* not implemented, not used
            ACTION_DOWNLOAD_EPISODE_SUBTITLE -> {
                loadLinks(click.data, isVisible =  false, isCasting = false) { links ->
                    downloadSubtitle(activity,links.subs,)
                }
            }*/
            ACTION_DOWNLOAD_EPISODE_SUBTITLE_MIRROR -> {
                val response = currentResponse ?: return

                acquireSingleSubtitle(
                    click.data,
                    txt(R.string.episode_action_download_subtitle)
                ) { (links, index) ->
                    downloadSubtitle(
                        activity,
                        links.subs[index],
                        getMeta(
                            click.data,
                            response.name,
                            response.apiName,
                            response.posterUrl,
                            response.isMovie(),
                            response.type
                        )
                    )
                    showToast(
                        R.string.download_started,
                        Toast.LENGTH_SHORT
                    )
                }
            }

            ACTION_SHOW_TOAST -> {
                showToast(R.string.play_episode_toast, Toast.LENGTH_SHORT)
            }

            ACTION_DOWNLOAD_EPISODE -> {
                val response = currentResponse ?: return
                downloadEpisode(
                    activity,
                    click.data,
                    response.isMovie(),
                    response.name,
                    response.type,
                    response.posterUrl,
                    response.apiName,
                    response.getId(),
                    response.url
                )
            }

            ACTION_DOWNLOAD_MIRROR -> {
                val response = currentResponse ?: return
                acquireSingleLink(
                    click.data,
                    LoadType.InAppDownload,
                    txt(R.string.episode_action_download_mirror)
                ) { (result, index) ->
                    ioSafe {
                        startDownload(
                            activity,
                            click.data,
                            response.isMovie(),
                            response.name,
                            response.type,
                            response.posterUrl,
                            response.apiName,
                            response.getId(),
                            response.url,
                            listOf(result.links[index]),
                            result.subs,
                        )
                    }
                    showToast(
                        R.string.download_started,
                        Toast.LENGTH_SHORT
                    )
                }
            }

            ACTION_RELOAD_EPISODE -> {
                ioSafe {
                    loadLinks(
                        click.data,
                        isVisible = false,
                        type = LoadType.InApp,
                        clearCache = true
                    )
                }
                showToast(
                    R.string.links_reloaded_toast,
                    Toast.LENGTH_SHORT
                )
            }

            ACTION_CHROME_CAST_MIRROR -> {
                acquireSingleLink(
                    click.data,
                    LoadType.Chromecast,
                    txt(R.string.episode_action_chromecast_mirror)
                ) { (result, index) ->
                    startChromecast(activity, click.data, result.links, result.subs, index)
                }
            }

            ACTION_PLAY_EPISODE_IN_BROWSER -> acquireSingleLink(
                click.data,
                LoadType.Browser,
                txt(R.string.episode_action_play_in_browser)
            ) { (result, index) ->
                try {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(result.links[index].url)
                    activity?.startActivity(i)
                } catch (e: Exception) {
                    logError(e)
                }
            }

            ACTION_COPY_LINK -> {
                acquireSingleLink(
                    click.data,
                    LoadType.ExternalApp,
                    txt(R.string.episode_action_copy_link)
                ) { (result, index) ->
                    val act = activity ?: return@acquireSingleLink
                    val serviceClipboard =
                        (act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)
                            ?: return@acquireSingleLink
                    val link = result.links[index]
                    val clip = ClipData.newPlainText(link.name, link.url)
                    serviceClipboard.setPrimaryClip(clip)
                    showToast(R.string.copy_link_toast, Toast.LENGTH_SHORT)
                }
            }

            ACTION_CHROME_CAST_EPISODE -> {
                startChromecast(activity, click.data)
            }

            ACTION_PLAY_EPISODE_IN_VLC_PLAYER -> {
                loadLinks(click.data, isVisible = true, LoadType.ExternalApp) { links ->
                    if (links.links.isEmpty()) {
                        showToast(R.string.no_links_found_toast, Toast.LENGTH_SHORT)
                        return@loadLinks
                    }

                    playWithVlc(
                        activity,
                        links,
                        click.data.id
                    )
                }
            }

            ACTION_PLAY_EPISODE_IN_WEB_VIDEO -> acquireSingleLink(
                click.data,
                LoadType.Chromecast,
                txt(
                    R.string.episode_action_play_in_format,
                    txt(R.string.player_settings_play_in_web)
                )
            ) { (result, index) ->
                playInWebVideo(
                    activity,
                    result.links[index],
                    click.data.name ?: click.data.headerName,
                    click.data.poster,
                    result.subs
                )
            }

            ACTION_PLAY_EPISODE_IN_MPV -> acquireSingleLink(
                click.data,
                LoadType.Chromecast,
                txt(
                    R.string.episode_action_play_in_format,
                    txt(R.string.player_settings_play_in_mpv)
                )
            ) { (result, index) ->
                playWithMpv(
                    activity,
                    click.data.id,
                    result.links[index],
                    result.subs
                )
            }

            ACTION_PLAY_EPISODE_IN_PLAYER -> {
                val data = currentResponse?.syncData?.toList() ?: emptyList()
                val list =
                    HashMap<String, String>().apply { putAll(data) }

                activity?.navigate(
                    R.id.global_to_navigation_player,
                    GeneratorPlayer.newInstance(
                        generator?.also {
                            it.getAll() // I know kinda shit to iterate all, but it is 100% sure to work
                                ?.indexOfFirst { value -> value is ResultEpisode && value.id == click.data.id }
                                ?.let { index ->
                                    if (index >= 0)
                                        it.goto(index)
                                }
                        } ?: return, list
                    )
                )
            }

            ACTION_MARK_AS_WATCHED -> {
                val isWatched =
                    getVideoWatchState(click.data.id) == VideoWatchState.Watched

                if (isWatched) {
                    setVideoWatchState(click.data.id, VideoWatchState.None)
                } else {
                    setVideoWatchState(click.data.id, VideoWatchState.Watched)
                }

                // Kinda dirty to reload all episodes :(
                reloadEpisodes()
            }
        }
    }

    private suspend fun applyMeta(
        resp: LoadResponse,
        meta: SyncAPI.SyncResult?,
        syncs: Map<String, String>? = null
    ): Pair<LoadResponse, Boolean> {
        //if (meta == null) return resp to false
        var updateEpisodes = false
        val out = resp.apply {
            Log.i(TAG, "applyMeta")

            if (meta != null) {
                duration = duration ?: meta.duration
                rating = rating ?: meta.publicScore
                tags = tags ?: meta.genres
                plot = if (plot.isNullOrBlank()) meta.synopsis else plot
                posterUrl = posterUrl ?: meta.posterUrl ?: meta.backgroundPosterUrl
                actors = actors ?: meta.actors

                if (this is EpisodeResponse) {
                    nextAiring = nextAiring ?: meta.nextAiring
                }

                val realRecommendations = ArrayList<SearchResponse>()
                val apiNames = synchronized(apis) {
                    apis.filter {
                        it.name.contains("gogoanime", true) ||
                                it.name.contains("9anime", true)
                    }.map {
                        it.name
                    }
                }
                meta.recommendations?.forEach { rec ->
                    apiNames.forEach { name ->
                        realRecommendations.add(rec.copy(apiName = name))
                    }
                }

                recommendations = recommendations?.union(realRecommendations)?.toList()
                    ?: realRecommendations
            }

            for ((k, v) in syncs ?: emptyMap()) {
                syncData[k] = v
            }

            argamap(
                {
                    if (this !is AnimeLoadResponse) return@argamap
                    // already exist, no need to run getTracker
                    if (this.getAniListId() != null && this.getMalId() != null) return@argamap

                    val res = APIHolder.getTracker(
                        listOfNotNull(
                            this.engName,
                            this.name,
                            this.japName
                        ).filter { it.length > 2 }
                            .distinct().map {
                                // this actually would be nice if we improved a bit as 3rd season == season 3 == III ect
                                // right now it just removes the dubbed status
                                it.lowercase().replace(Regex("""\(?[ds]ub(bed)?\)?(\s|$)""") , "").trim()
                            },
                        TrackerType.getTypes(this.type),
                        this.year
                    )

                    val ids = arrayOf(
                        AccountManager.malApi.idPrefix to res?.malId?.toString(),
                        AccountManager.aniListApi.idPrefix to res?.aniId
                    )

                    if (ids.any { (id, new) ->
                            val current = syncData[id]
                            new != null && current != null && current != new
                        }
                    ) {
                        // getTracker fucked up as it conflicts with current implementation
                        return@argamap
                    }

                    // set all the new data, prioritise old correct data
                    ids.forEach { (id, new) ->
                        new?.let {
                            syncData[id] = syncData[id] ?: it
                        }
                    }

                    // set posters, might fuck up due to headers idk
                    posterUrl = posterUrl ?: res?.image
                    backgroundPosterUrl = backgroundPosterUrl ?: res?.cover
                },
                {
                    if (meta == null) return@argamap
                    addTrailer(meta.trailers)
                }, {
                    if (this !is AnimeLoadResponse) return@argamap
                    val map =
                        Kitsu.getEpisodesDetails(
                            getMalId(),
                            getAniListId(),
                            isResponseRequired = false
                        )
                    if (map.isNullOrEmpty()) return@argamap
                    updateEpisodes = DubStatus.values().map { dubStatus ->
                        val current =
                            this.episodes[dubStatus]?.mapIndexed { index, episode ->
                                episode.apply {
                                    this.episode = this.episode ?: (index + 1)
                                }
                            }?.sortedBy { it.episode ?: 0 }?.toMutableList()
                        if (current.isNullOrEmpty()) return@map false
                        val episodeNumbers = current.map { ep -> ep.episode!! }
                        var updateCount = 0
                        map.forEach { (episode, node) ->
                            episodeNumbers.binarySearch(episode).let { index ->
                                current.getOrNull(index)?.let { currentEp ->
                                    current[index] = currentEp.apply {
                                        updateCount++
                                        this.description = this.description ?: node.description?.en
                                        this.name = this.name ?: node.titles?.canonical
                                        this.episode =
                                            this.episode ?: node.num ?: episodeNumbers[index]
                                        this.posterUrl =
                                            this.posterUrl ?: node.thumbnail?.original?.url
                                    }
                                }
                            }
                        }
                        this.episodes[dubStatus] = current
                        updateCount > 0
                    }.any { it }
                })
        }
        return out to updateEpisodes
    }

    fun setMeta(meta: SyncAPI.SyncResult, syncs: Map<String, String>?) {
        // I dont want to update everything if the metadata is not relevant
        if (currentMeta == meta && currentSync == syncs) {
            Log.i(TAG, "setMeta same")
            return
        }
        Log.i(TAG, "setMeta")
        viewModelScope.launchSafe {
            currentMeta = meta
            currentSync = syncs
            val (value, updateEpisodes) = ioWork {
                currentResponse?.let { resp ->
                    return@ioWork applyMeta(resp, meta, syncs)
                }
                return@ioWork null to null
            }

            postSuccessful(
                value ?: return@launchSafe,
                currentId ?: return@launchSafe,
                currentRepo ?: return@launchSafe,
                updateEpisodes ?: return@launchSafe,
                false
            )
        }
    }


    private suspend fun updateFillers(name: String) {
        fillers =
            ioWorkSafe {
                FillerEpisodeCheck.getFillerEpisodes(name)
            } ?: emptyMap()
    }

    fun changeDubStatus(status: DubStatus) {
        postEpisodeRange(currentIndex?.copy(dubStatus = status), currentRange)
    }

    fun changeRange(range: EpisodeRange) {
        postEpisodeRange(currentIndex, range)
    }

    fun changeSeason(season: Int) {
        postEpisodeRange(currentIndex?.copy(season = season), currentRange)
    }

    private fun getMovie(): ResultEpisode? {
        return currentEpisodes.entries.firstOrNull()?.value?.firstOrNull()?.let { ep ->
            val posDur = getViewPos(ep.id)
            ep.copy(position = posDur?.position ?: 0, duration = posDur?.duration ?: 0)
        }
    }

    private fun getEpisodes(indexer: EpisodeIndexer, range: EpisodeRange): List<ResultEpisode> {
        val startIndex = range.startIndex
        val length = range.length

        return currentEpisodes[indexer]
            ?.let { list ->
                val start = minOf(list.size, startIndex)
                val end = minOf(list.size, start + length)
                list.subList(start, end).map {
                    val posDur = getViewPos(it.id)
                    val watchState =
                        getVideoWatchState(it.id) ?: VideoWatchState.None
                    it.copy(
                        position = posDur?.position ?: 0,
                        duration = posDur?.duration ?: 0,
                        videoWatchState = watchState
                    )
                }
            }
            ?: emptyList()
    }

    private fun postMovie() {
        val response = currentResponse
        _episodes.postValue(null)

        if (response == null) {
            _movie.postValue(null)
            return
        }

        val text = txt(
            when (response.type) {
                TvType.Torrent -> R.string.play_torrent_button
                else -> {
                    if (response.type.isLiveStream())
                        R.string.play_livestream_button
                    else if (response.isMovie()) // this wont break compatibility as you only need to override isMovieType
                        R.string.play_movie_button
                    else null
                }
            }
        )
        val data = getMovie()
        _episodes.postValue(null)
        if (text == null || data == null) {
            _movie.postValue(null)
        } else {
            _movie.postValue(Resource.Success(text to data))
        }
    }

    fun reloadEpisodes() {
        if (currentResponse?.isMovie() == true) {
            postMovie()
        } else {
            _episodes.postValue(
                Resource.Success(
                    getEpisodes(
                        currentIndex ?: return,
                        currentRange ?: return
                    )
                )
            )
            _movie.postValue(null)
        }
        postResume()
    }

    private fun postSubscription(loadResponse: LoadResponse) {
        if (loadResponse.isEpisodeBased()) {
            val id = loadResponse.getId()
            val data = getSubscribedData(id)
            updateSubscribedData(id, data, loadResponse as? EpisodeResponse)
            val isSubscribed = data != null
            _subscribeStatus.postValue(isSubscribed)
        }
    }

    private fun postFavorites(loadResponse: LoadResponse) {
        val id = loadResponse.getId()
        val isFavorite = getFavoritesData(id) != null
        _favoriteStatus.postValue(isFavorite)
    }

    private fun postEpisodeRange(indexer: EpisodeIndexer?, range: EpisodeRange?) {
        if (range == null || indexer == null) {
            return
        }

        val ranges = currentRanges[indexer]

        if (ranges?.contains(range) != true) {
            // if the current ranges does not include the range then select the range with the closest matching start episode
            // this usually happends when dub has less episodes then sub -> the range does not exist
            ranges?.minByOrNull { kotlin.math.abs(it.startEpisode - range.startEpisode) }
                ?.let { r ->
                    postEpisodeRange(indexer, r)
                    return
                }
        }

        val isMovie = currentResponse?.isMovie() == true
        currentIndex = indexer
        currentRange = range

        _rangeSelections.postValue(ranges?.map { r ->
            val text = txt(R.string.episodes_range, r.startEpisode, r.endEpisode)
            text to r
        } ?: emptyList())

        val size = currentEpisodes[indexer]?.size
        _episodesCountText.postValue(

            if (isMovie) null else
                txt(
                    R.string.episode_format,
                    size,
                    txt(if (size == 1) R.string.episode else R.string.episodes),
                )

        )

        _selectedSeasonIndex.postValue(
            currentSeasons.indexOf(indexer.season)
        )

        _selectedSeason.postValue(

            if (isMovie || currentSeasons.size <= 1) null else
                when (indexer.season) {
                    0 -> txt(R.string.no_season)
                    else -> {
                        val seasonNames = (currentResponse as? EpisodeResponse)?.seasonNames
                        val seasonData = seasonNames.getSeason(indexer.season)

                        // If displaySeason is null then only show the name!
                        if (seasonData?.name != null && seasonData.displaySeason == null) {
                            txt(seasonData.name)
                        } else {
                            val suffix = seasonData?.name?.let { " $it" } ?: ""
                            txt(
                                R.string.season_format,
                                txt(R.string.season),
                                seasonData?.displaySeason ?: indexer.season,
                                suffix
                            )
                        }
                    }
                }

        )

        _selectedRangeIndex.postValue(
            ranges?.indexOf(range) ?: -1
        )

        _selectedRange.postValue(

            if (isMovie) null else if ((currentRanges[indexer]?.size ?: 0) > 1) {
                txt(R.string.episodes_range, range.startEpisode, range.endEpisode)
            } else {
                null
            }

        )

        _selectedDubStatusIndex.postValue(
            currentDubStatus.indexOf(indexer.dubStatus)
        )

        _selectedDubStatus.postValue(

            if (isMovie || currentDubStatus.size <= 1) null else
                txt(indexer.dubStatus)

        )

        currentId?.let { id ->
            setDub(id, indexer.dubStatus)
            setResultSeason(id, indexer.season)
            setResultEpisode(id, range.startEpisode)
        }

        preferStartEpisode = range.startEpisode
        preferStartSeason = indexer.season
        preferDubStatus = indexer.dubStatus

        generator = if (isMovie) {
            getMovie()?.let { RepoLinkGenerator(listOf(it), page = currentResponse) }
        } else {
            val episodes = currentEpisodes.filter { it.key.dubStatus == indexer.dubStatus }
                .toList()
                .sortedBy { it.first.season }
                .flatMap { it.second }

            RepoLinkGenerator(episodes, page = currentResponse)
        }

        if (isMovie) {
            postMovie()
        } else {
            val ret = getEpisodes(indexer, range)
            /*if (ret.isEmpty()) {
                val index = ranges?.indexOf(range)
                if(index != null && index > 0) {

                }
            }*/
            _episodes.postValue(Resource.Success(ret))
        }
    }

    private suspend fun postSuccessful(
        loadResponse: LoadResponse,
        mainId : Int,
        apiRepository: APIRepository,
        updateEpisodes: Boolean,
        updateFillers: Boolean,
    ) {
        currentId = mainId
        currentResponse = loadResponse
        postPage(loadResponse, apiRepository)
        postSubscription(loadResponse)
        postFavorites(loadResponse)
        _watchStatus.postValue(getResultWatchState(mainId))

        if (updateEpisodes)
            postEpisodes(loadResponse, mainId, updateFillers)
    }

    private suspend fun postEpisodes(loadResponse: LoadResponse, mainId : Int, updateFillers: Boolean) {
        _episodes.postValue(Resource.Loading())

        if (updateFillers && loadResponse is AnimeLoadResponse) {
            updateFillers(loadResponse.name)
        }

        val allEpisodes = when (loadResponse) {
            is AnimeLoadResponse -> {
                val existingEpisodes = HashSet<Int>()
                val episodes: MutableMap<EpisodeIndexer, MutableList<ResultEpisode>> =
                    mutableMapOf()
                loadResponse.episodes.map { ep ->
                    val idIndex = ep.key.id
                    for ((index, i) in ep.value.withIndex()) {
                        val episode = i.episode ?: (index + 1)
                        val id =
                            mainId + episode + idIndex * 1_000_000 + (i.season?.times(10_000)
                                ?: 0)

                        val totalIndex =
                            i.season?.let { season -> loadResponse.getTotalEpisodeIndex(episode, season) }

                        if (!existingEpisodes.contains(id)) {
                            existingEpisodes.add(id)
                            val seasonData = loadResponse.seasonNames.getSeason(i.season)
                            val eps =
                                buildResultEpisode(
                                    loadResponse.name,
                                    filterName(i.name),
                                    i.posterUrl,
                                    episode,
                                    seasonData?.season ?: i.season,
                                    if (seasonData != null) seasonData.displaySeason else i.season,
                                    i.data,
                                    loadResponse.apiName,
                                    id,
                                    index,
                                    i.rating,
                                    i.description,
                                    fillers.getOrDefault(episode, false),
                                    loadResponse.type,
                                    mainId,
                                    totalIndex
                                )

                            val season = eps.seasonIndex ?: 0
                            val indexer = EpisodeIndexer(ep.key, season)
                            episodes[indexer]?.add(eps) ?: run {
                                episodes[indexer] = mutableListOf(eps)
                            }
                        }
                    }
                }
                episodes
            }

            is TvSeriesLoadResponse -> {
                val episodes: MutableMap<EpisodeIndexer, MutableList<ResultEpisode>> =
                    mutableMapOf()
                val existingEpisodes = HashSet<Int>()
                for ((index, episode) in loadResponse.episodes.sortedBy {
                    (it.season?.times(10_000) ?: 0) + (it.episode ?: 0)
                }.withIndex()) {
                    val episodeIndex = episode.episode ?: (index + 1)
                    val id =
                        mainId + (episode.season?.times(100_000) ?: 0) + episodeIndex + 1
                    if (!existingEpisodes.contains(id)) {
                        existingEpisodes.add(id)
                        val seasonData =
                            loadResponse.seasonNames.getSeason(episode.season)

                        val totalIndex =
                            episode.season?.let { season -> loadResponse.getTotalEpisodeIndex(episodeIndex, season) }

                        val ep =
                            buildResultEpisode(
                                loadResponse.name,
                                filterName(episode.name),
                                episode.posterUrl,
                                episodeIndex,
                                seasonData?.season ?: episode.season,
                                if (seasonData != null) seasonData.displaySeason else episode.season,
                                episode.data,
                                loadResponse.apiName,
                                id,
                                index,
                                episode.rating,
                                episode.description,
                                null,
                                loadResponse.type,
                                mainId,
                                totalIndex
                            )

                        val season = ep.seasonIndex ?: 0
                        val indexer = EpisodeIndexer(DubStatus.None, season)

                        episodes[indexer]?.add(ep) ?: kotlin.run {
                            episodes[indexer] = mutableListOf(ep)
                        }
                    }
                }
                episodes
            }

            is MovieLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.dataUrl,
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId,
                        null
                    )
                )
            }

            is LiveStreamLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.dataUrl,
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId,
                        null
                    )
                )
            }

            is TorrentLoadResponse -> {
                singleMap(
                    buildResultEpisode(
                        loadResponse.name,
                        loadResponse.name,
                        null,
                        0,
                        null,
                        null,
                        loadResponse.torrent ?: loadResponse.magnet ?: "",
                        loadResponse.apiName,
                        (mainId), // HAS SAME ID
                        0,
                        null,
                        null,
                        null,
                        loadResponse.type,
                        mainId,
                        null
                    )
                )
            }

            else -> {
                mapOf()
            }
        }

        val seasonsSelection = mutableSetOf<Int>()
        val dubSelection = mutableSetOf<DubStatus>()
        allEpisodes.keys.forEach { key ->
            seasonsSelection += key.season
            dubSelection += key.dubStatus
        }
        currentDubStatus = dubSelection.toList()
        currentSeasons = seasonsSelection.toList()
        _dubSubSelections.postValue(dubSelection.map { txt(it) to it })
        if (loadResponse is EpisodeResponse) {
            _seasonSelections.postValue(seasonsSelection.map { seasonNumber ->
                val seasonData = loadResponse.seasonNames.getSeason(seasonNumber)
                val fixedSeasonNumber = seasonData?.displaySeason ?: seasonNumber
                val suffix = seasonData?.name?.let { " $it" } ?: ""
                // If displaySeason is null then only show the name!
                val name = if (seasonData?.name != null && seasonData.displaySeason == null) {
                    txt(seasonData.name)
                } else {
                    txt(
                        R.string.season_format,
                        txt(R.string.season),
                        fixedSeasonNumber,
                        suffix
                    )
                }
                name to seasonNumber
            })
        }

        currentEpisodes = allEpisodes
        val ranges = getRanges(allEpisodes, EPISODE_RANGE_SIZE)
        currentRanges = ranges


        // this takes the indexer most preferable by the user given the current sorting
        val min = ranges.keys.minByOrNull { index ->
            kotlin.math.abs(
                index.season - (preferStartSeason ?: 1)
            ) + if (index.dubStatus == preferDubStatus) 0 else 100000
        }

        // this takes the range most preferable by the user given the current sorting
        val ranger = ranges[min]
        val range = ranger?.firstOrNull {
            it.startEpisode >= (preferStartEpisode ?: 0)
        } ?: ranger?.lastOrNull()

        postEpisodeRange(min, range)
        postResume()
    }

    private fun postResume() {
        _resumeWatching.postValue(resume())
    }

    private fun resume(): ResumeWatchingStatus? {
        val correctId = currentId ?: return null
        val resume = getLastWatched(correctId)
        val resumeParentId = resume?.parentId
        if (resumeParentId != correctId) return null // is null or smth went wrong with getLastWatched
        val resumeId = resume.episodeId ?: return null// invalid episode id
        val response = currentResponse ?: return null
        // kinda ugly ik
        val episode =
            currentEpisodes.values.flatten().firstOrNull { it.id == resumeId } ?: return null

        val isMovie = response.isMovie()

        val progress = getViewPos(resume.episodeId)?.let { viewPos ->
            ResumeProgress(
                progress = (viewPos.position / 1000).toInt(),
                maxProgress = (viewPos.duration / 1000).toInt(),
                txt(R.string.resume_time_left, (viewPos.duration - viewPos.position) / (60_000))
            )
        }

        return ResumeWatchingStatus(progress = progress, isMovie = isMovie, result = episode)
    }

    private fun loadTrailers(loadResponse: LoadResponse) = ioSafe {
        _trailers.postValue(
            getTrailers(
                loadResponse,
                3
            )
        ) // we dont want to fetch too many trailers
    }

    private suspend fun getTrailers(
        loadResponse: LoadResponse,
        limit: Int = 0
    ): List<ExtractedTrailerData> =
        coroutineScope {
            val returnlist = ArrayList<ExtractedTrailerData>()
            loadResponse.trailers.windowed(limit, limit, true).takeWhile { list ->
                list.amap { trailerData ->
                    try {
                        val links = arrayListOf<ExtractorLink>()
                        val subs = arrayListOf<SubtitleFile>()
                        if (!loadExtractor(
                                trailerData.extractorUrl,
                                trailerData.referer,
                                { subs.add(it) },
                                { links.add(it) }) && trailerData.raw
                        ) {
                            arrayListOf(
                                ExtractorLink(
                                    "",
                                    "Trailer",
                                    trailerData.extractorUrl,
                                    trailerData.referer ?: "",
                                    Qualities.Unknown.value,
                                    type = INFER_TYPE
                                )
                            ) to arrayListOf()
                        } else {
                            links to subs
                        }
                    } catch (e: Throwable) {
                        logError(e)
                        null
                    }
                }.filterNotNull().map { (links, subs) -> ExtractedTrailerData(links, subs) }.let {
                    returnlist.addAll(it)
                }

                returnlist.size < limit
            }
            return@coroutineScope returnlist
        }


    // this instantly updates the metadata on the page
    private fun postPage(loadResponse: LoadResponse, apiRepository: APIRepository) {
        _recommendations.postValue(loadResponse.recommendations ?: emptyList())
        _page.postValue(Resource.Success(loadResponse.toResultData(apiRepository)))
    }

    fun hasLoaded() = currentResponse != null

    private fun handleAutoStart(activity: Activity?, autostart: AutoResume?) =
        viewModelScope.launchSafe {
            if (autostart == null || activity == null) return@launchSafe

            when (autostart.startAction) {
                START_ACTION_RESUME_LATEST -> {
                    currentEpisodes[currentIndex]?.let { currentRange ->
                        for (ep in currentRange) {
                            if (ep.getWatchProgress() > 0.9) continue
                            handleAction(
                                EpisodeClickEvent(
                                    getPlayerAction(activity),
                                    ep
                                )
                            )
                            break
                        }
                    }
                }

                START_ACTION_LOAD_EP -> {
                    val all = currentEpisodes.values.flatten()
                    val episode =
                        autostart.id?.let { id -> all.firstOrNull { it.id == id } }
                            ?: autostart.episode?.let { ep ->
                                currentEpisodes[currentIndex]?.firstOrNull { it.episode == ep && it.season == autostart.episode }
                                    ?: all.firstOrNull { it.episode == ep && it.season == autostart.episode }
                            }
                            ?: return@launchSafe
                    handleAction(
                        EpisodeClickEvent(
                            getPlayerAction(activity),
                            episode
                        )
                    )
                }
            }
        }

    data class LoadResponseFromSearch(
        override var name: String,
        override var url: String,
        override var apiName: String,
        override var type: TvType,
        override var posterUrl: String?,
        override var year: Int? = null,
        override var plot: String? = null,
        override var rating: Int? = null,
        override var tags: List<String>? = null,
        override var duration: Int? = null,
        override var trailers: MutableList<TrailerData> = mutableListOf(),
        override var recommendations: List<SearchResponse>? = null,
        override var actors: List<ActorData>? = null,
        override var comingSoon: Boolean = false,
        override var syncData: MutableMap<String, String> = mutableMapOf(),
        override var posterHeaders: Map<String, String>? = null,
        override var backgroundPosterUrl: String? = null,
        override var contentRating: String? = null,
    ) : LoadResponse

    fun loadSmall(activity: Activity?, searchResponse : SearchResponse) = ioSafe {
        val url = searchResponse.url
        _page.postValue(Resource.Loading(url))
        _episodes.postValue(Resource.Loading())
        val api = APIHolder.getApiFromNameNull(searchResponse.apiName) ?: APIHolder.getApiFromUrlNull(searchResponse.url) ?: APIRepository.noneApi
        val repo = APIRepository(api)
        val response = LoadResponseFromSearch(name = searchResponse.name, url = searchResponse.url, apiName = api.name, type = searchResponse.type ?: TvType.Others,
            posterUrl = searchResponse.posterUrl).apply {
            if (searchResponse is SyncAPI.LibraryItem) {
                this.plot = searchResponse.plot
                this.rating = searchResponse.personalRating?.times(100) ?: searchResponse.rating
                this.tags = searchResponse.tags
            }
        }
        val mainId = searchResponse.id ?: response.getId()

        postSuccessful(
            loadResponse = response,
            mainId = mainId,
            apiRepository = repo, updateEpisodes = false, updateFillers =  false)
    }

    fun load(
        activity: Activity?,
        url: String,
        apiName: String,
        showFillers: Boolean,
        dubStatus: DubStatus,
        autostart: AutoResume?,
        loadTrailers: Boolean = true,
    ) =
        ioSafe {
            _page.postValue(Resource.Loading(url))
            _episodes.postValue(Resource.Loading())

            preferDubStatus = dubStatus
            currentShowFillers = showFillers

            // set api
            val api = APIHolder.getApiFromNameNull(apiName) ?: APIHolder.getApiFromUrlNull(url)
            if (api == null) {
                _page.postValue(
                    Resource.Failure(
                        false,
                        null,
                        null,
                        "This provider does not exist"
                    )
                )
                return@ioSafe
            }


            // validate url
            val validUrlResource = safeApiCall {
                SyncRedirector.redirect(
                    url,
                    api
                )
            }

            if (validUrlResource !is Resource.Success) {
                if (validUrlResource is Resource.Failure) {
                    _page.postValue(validUrlResource)
                }

                return@ioSafe
            }

            val validUrl = validUrlResource.value
            val repo = APIRepository(api)
            currentRepo = repo

            when (val data = repo.load(validUrl)) {
                is Resource.Failure -> {
                    _page.postValue(data)
                }

                is Resource.Success -> {
                    if (!isActive) return@ioSafe
                    val loadResponse = ioWork {
                        applyMeta(data.value, currentMeta, currentSync).first
                    }
                    if (!isActive) return@ioSafe
                    val mainId = loadResponse.getId()

                    preferDubStatus = getDub(mainId) ?: preferDubStatus
                    preferStartEpisode = getResultEpisode(mainId)
                    preferStartSeason = getResultSeason(mainId) ?: 1

                    setKey(
                        DOWNLOAD_HEADER_CACHE,
                        mainId.toString(),
                        VideoDownloadHelper.DownloadHeaderCached(
                            apiName,
                            validUrl,
                            loadResponse.type,
                            loadResponse.name,
                            loadResponse.posterUrl,
                            mainId,
                            System.currentTimeMillis(),
                        )
                    )
                    if (loadTrailers)
                        loadTrailers(data.value)
                    postSuccessful(
                        data.value,
                        mainId,
                        updateEpisodes = true,
                        updateFillers = showFillers,
                        apiRepository = repo
                    )
                    if (!isActive) return@ioSafe
                    handleAutoStart(activity, autostart)
                }

                is Resource.Loading -> {
                    debugException { "Invalid load result" }
                }
            }
        }
}
