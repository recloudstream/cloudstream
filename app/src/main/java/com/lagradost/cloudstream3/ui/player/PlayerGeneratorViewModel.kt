package com.lagradost.cloudstream3.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getLinkPriority
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.videoskip.SkipAPI
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Contract
import java.util.concurrent.ConcurrentHashMap

typealias VideoLink = Pair<ExtractorLink?, ExtractorUri?>

data class GeneratorState(
    val meta: Any?,
    val nextMeta: Any?,
    val allMeta: List<*>?,
    val response: LoadResponse?,
    val index: Int,
    val id: Int?,
)

/** Immutable state of all current links relevant to displaying the video */
// @MustUseReturnValues
// @Immutable
data class VideoState(
    val subtitles: PersistentSet<SubtitleData> = persistentSetOf(),
    val links: PersistentSet<VideoLink> = persistentSetOf(),
    val stamps: PersistentList<VideoSkipStamp> = persistentListOf(),
    val loading: Resource<Unit> = Resource.Loading(),
    val generatorState: GeneratorState? = null,
    val instance: Int,
) {
    /**
     * This acts as a local cache for sorted links that are not copied over by the copy constructor.
     *
     * sortedBy is not exactly expensive, but each hasNextMirror does it again, so this alleviates unnecessary recomputation
     * */
    private val sortedLinks: ConcurrentHashMap<Int, List<VideoLink>> = ConcurrentHashMap()

    fun clearSortedLinksCache() = sortedLinks.clear()

    // Modifying sortedLinks is not considered a "visible" side effect, and rerunning it does not change the result
    // It is by all standards, idempotent and by extension also pure as it has no "visible" side effect
    /** Returns .links in the sorted order according to the qualityProfile.
     * Use .links if order is not needed */
    @Contract(pure = true)
    fun sortLinks(qualityProfile: Int): List<VideoLink> {
        return sortedLinks[qualityProfile] ?: links.sortedBy { link ->
            // negative because we want to sort highest quality first
            -getLinkPriority(qualityProfile, link.first)
        }.also { value -> sortedLinks[qualityProfile] = value }
    }

    @Contract(pure = true)
    fun add(item: SubtitleData): VideoState = copy(subtitles = subtitles.add(item))

    @Contract(pure = true)
    fun add(item: VideoLink): VideoState = copy(links = links.add(item))

    @Contract(pure = true)
    fun add(item: VideoSkipStamp): VideoState = copy(stamps = stamps.add(item))

    @JvmName("addSubtitleData")
    @Contract(pure = true)
    fun add(items: Collection<SubtitleData>): VideoState = copy(subtitles = subtitles.addAll(items))

    @JvmName("addVideoLink")
    @Contract(pure = true)
    fun add(items: Collection<VideoLink>): VideoState = copy(links = links.addAll(items))

    @JvmName("addVideoSkipStamp")
    @Contract(pure = true)
    fun add(items: Collection<VideoSkipStamp>): VideoState = copy(stamps = stamps.addAll(items))

    @Contract(pure = true)
    fun set(item: SubtitleData): VideoState = copy(subtitles = persistentSetOf(item))

    @Contract(pure = true)
    fun set(item: VideoLink): VideoState = copy(links = persistentSetOf(item))

    @Contract(pure = true)
    fun set(item: VideoSkipStamp): VideoState = copy(stamps = persistentListOf(item))

    @JvmName("setSubtitleData")
    @Contract(pure = true)
    fun set(items: Collection<SubtitleData>): VideoState = copy(subtitles = items.toPersistentSet())

    @JvmName("setVideoLink")
    @Contract(pure = true)
    fun set(items: Collection<VideoLink>): VideoState = copy(links = items.toPersistentSet())

    @JvmName("setVideoSkipStamp")
    @Contract(pure = true)
    fun set(items: Collection<VideoSkipStamp>): VideoState = copy(stamps = items.toPersistentList())
}

data class VideoLive<T>(
    val value: T,
    val instance: Int,
)

class PlayerGeneratorViewModel : ViewModel() {
    companion object {
        const val TAG = "PlayViewGen"
    }

    @Volatile
    var generator: VideoGenerator<*>? = null

    @Volatile
    var episodeIndex: Int = 0

    /**
     * The state of the video player, only modify it by modifyState to make sure observe is called,
     * and avoid concurrency issues.
     *
     * This value can be used without Synchronized or locking when reading, as all fields are immutable.
     * */
    @Volatile
    var state = VideoState(instance = 0)
        private set

    private val _currentLinks =
        MutableLiveData<VideoLive<Set<Pair<ExtractorLink?, ExtractorUri?>>>>(null)
    val currentLinks: LiveData<VideoLive<Set<Pair<ExtractorLink?, ExtractorUri?>>>> = _currentLinks

    private val _currentSubtitles = MutableLiveData<VideoLive<Set<SubtitleData>>>(null)
    val currentSubtitles: LiveData<VideoLive<Set<SubtitleData>>> = _currentSubtitles

    private val _loadingLinks = MutableLiveData<VideoLive<Resource<Unit>>>()
    val loadingLinks: LiveData<VideoLive<Resource<Unit>>> = _loadingLinks

    private val _currentStamps = MutableLiveData<VideoLive<List<VideoSkipStamp>>>(null)
    val currentStamps: LiveData<VideoLive<List<VideoSkipStamp>>> = _currentStamps

    /**
     * Modifies the `state` variable safely, and with the correct observe behavior.
     *
     * Synchronized to avoid concurrency issues, and make this operation atomic.
     * Otherwise, one update may be lost if they are done in parallel.
     * */
    @Synchronized
    fun modifyState(op: VideoState.() -> VideoState) {
        val oldState = state
        state = op.invoke(oldState)

        /** New instance, always push state */
        if (state.instance != oldState.instance) {
            _currentSubtitles.postValue(VideoLive(state.subtitles, state.instance))
            _currentStamps.postValue(VideoLive(state.stamps, state.instance))
            _currentLinks.postValue(VideoLive(state.links, state.instance))
            _loadingLinks.postValue(VideoLive(state.loading, state.instance))
            return
        }

        /**
         * Only post the changed values, this makes sure we do not invoke the "observe"
         *
         * We do this by "Referential equality" https://kotlinlang.org/docs/equality.html#referential-equality
         * to avoid comparing the entire set or list as "Persistent" classes will hold the same reference if they are unchanged.
         * */
        if (state.links !== oldState.links)
            _currentLinks.postValue(VideoLive(state.links, state.instance))
        if (state.stamps !== oldState.stamps)
            _currentStamps.postValue(VideoLive(state.stamps, state.instance))
        if (state.subtitles !== oldState.subtitles)
            _currentSubtitles.postValue(VideoLive(state.subtitles, state.instance))

        /** Normal equality here as it is not a collection */
        if (state.loading != oldState.loading)
            _loadingLinks.postValue(VideoLive(state.loading, state.instance))
    }

    private val _currentSubtitleYear = MutableLiveData<Int?>(null)
    val currentSubtitleYear: LiveData<Int?> = _currentSubtitleYear

    /**
     * Save the Episode ID to prevent starting multiple link loading Jobs when preloading links.
     */
    private var currentLoadingEpisodeId: Int? = null

    var forceClearCache = false

    fun setSubtitleYear(year: Int?) {
        _currentSubtitleYear.postValue(year)
    }

    fun loadLinksPrev() {
        Log.i(TAG, "loadLinksPrev")
        if (generator?.hasPrev(episodeIndex) == true) {
            episodeIndex += 1
            loadLinks()
        }
    }

    fun loadLinksNext() {
        Log.i(TAG, "loadLinksNext")
        if (generator?.hasNext(episodeIndex) == true) {
            episodeIndex += 1
            loadLinks()
        }
    }

    fun hasNextEpisode(): Boolean? {
        return generator?.hasNext(episodeIndex)
    }

    fun hasPrevEpisode(): Boolean? {
        return generator?.hasPrev(episodeIndex)
    }

    fun preLoadNextLinks() {
        val id = generator?.getId(episodeIndex)
        // Do not preload if already loading
        if (id == currentLoadingEpisodeId) return

        Log.i(TAG, "preLoadNextLinks")
        currentJob?.cancel()
        currentLoadingEpisodeId = id

        currentJob = viewModelScope.launch {
            try {
                if (generator?.hasCache == true && generator?.hasNext(episodeIndex) == true) {
                    safeApiCall {
                        generator?.generateLinks(
                            sourceTypes = LOADTYPE_INAPP,
                            clearCache = false,
                            isCasting = false,
                            callback = {},
                            subtitleCallback = {},
                            offset = episodeIndex + 1
                        )
                    }
                }
            } catch (t: Throwable) {
                logError(t)
            } finally {
                if (currentLoadingEpisodeId == id) {
                    currentLoadingEpisodeId = null
                }
            }
        }
    }

    fun loadThisEpisode(index: Int) {
        episodeIndex = index
        loadLinks()
    }

    fun attachGenerator(newGenerator: VideoGenerator<*>, index: Int) {
        Log.i(TAG, "attachGenerator with generator=$newGenerator and index=$index")
        generator = newGenerator
        episodeIndex = index
    }

    /**
     * If duplicate nothing will happen
     * */
    fun addSubtitles(file: Set<SubtitleData>) {
        val validFile = file.filter(::isValidSubtitle)
        if (validFile.isNotEmpty())
            modifyState {
                add(validFile)
            }
    }

    private var currentJob: Job? = null
    private var currentStampJob: Job? = null

    fun loadStamps(duration: Long) {
        currentStampJob = ioSafe {
            val genState = state.generatorState ?: return@ioSafe
            val meta = genState.meta
            val page = genState.response
            val id = genState.id
            if (page == null || meta !is ResultEpisode) {
                return@ioSafe
            }
            val stamps = SkipAPI.videoStamps(
                page,
                meta,
                duration,
                hasNextEpisode() ?: false
            )

            /** Avoid adding stamps to the wrong video */
            modifyState {
                if (id != this.generatorState?.id) {
                    this
                } else {
                    set(stamps)
                }
            }
        }
    }

    var langFilterList = listOf<String>()
    var filterSubByLang = false

    fun isValidSubtitle(subtitle: SubtitleData): Boolean {
        if (langFilterList.isEmpty() || !filterSubByLang) {
            return true
        }

        /** Only filter out subtitles fetched online */
        if (subtitle.origin != SubtitleOrigin.URL) {
            return true
        }

        return langFilterList.any { lang ->
            subtitle.originalName.contains(lang, ignoreCase = true)
        }
    }

    fun loadLinks(sourceTypes: Set<ExtractorLinkType> = LOADTYPE_INAPP) {
        Log.i(TAG, "loadLinks with generator=$generator and index=$episodeIndex")
        currentJob?.cancel()
        val index = episodeIndex

        // Clear old data and reset the state
        modifyState {
            VideoState(
                loading = Resource.Loading(),
                generatorState = generator?.let { gen ->
                    GeneratorState(
                        meta = gen.videos.getOrNull(index),
                        nextMeta = gen.videos.getOrNull(index + 1),
                        id = gen.getId(index),
                        response = (gen as? RepoLinkGenerator)?.page,
                        index = index,
                        allMeta = gen.videos
                    )
                },
                instance = instance + 1
            )
        }

        currentJob = viewModelScope.launchSafe {
            // Load more data
            val loadingState = safeApiCall {
                generator?.generateLinks(
                    sourceTypes = sourceTypes,
                    clearCache = forceClearCache,
                    callback = { link ->
                        if (isActive)
                            modifyState {
                                add(link)
                            }
                    },
                    isCasting = false,
                    offset = index,
                    subtitleCallback = { link ->
                        if (isActive && isValidSubtitle(link))
                            modifyState {
                                add(link)
                            }
                    })
                Unit
            }

            if (!isActive) {
                return@launchSafe
            }

            /** Only mark as success if we have not skipped loading */
            modifyState {
                if (!isActive) {
                    this
                } else {
                    when (loading) {
                        is Resource.Loading -> copy(loading = loadingState)
                        else -> this
                    }
                }
            }
        }
    }
}