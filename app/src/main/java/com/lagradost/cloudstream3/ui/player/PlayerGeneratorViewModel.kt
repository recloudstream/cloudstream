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
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerGeneratorViewModel : ViewModel() {
    companion object {
        const val TAG = "PlayViewGen"
    }

    private var generator: IGenerator? = null

    private val _currentLinks = MutableLiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>>(setOf())
    val currentLinks: LiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>> = _currentLinks

    private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
    val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

    private val _loadingLinks = MutableLiveData<Resource<Boolean?>>()
    val loadingLinks: LiveData<Resource<Boolean?>> = _loadingLinks

    private val _currentStamps = MutableLiveData<List<EpisodeSkip.SkipStamp>>(emptyList())
    val currentStamps: LiveData<List<EpisodeSkip.SkipStamp>> = _currentStamps

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

    fun getId(): Int? {
        return generator?.getCurrentId()
    }

    fun loadLinks(episode: Int) {
        generator?.goto(episode)
        loadLinks()
    }

    fun loadLinksPrev() {
        Log.i(TAG, "loadLinksPrev")
        if (generator?.hasPrev() == true) {
            generator?.prev()
            loadLinks()
        }
    }

    fun loadLinksNext() {
        Log.i(TAG, "loadLinksNext")
        if (generator?.hasNext() == true) {
            generator?.next()
            loadLinks()
        }
    }

    fun hasNextEpisode(): Boolean? {
        return generator?.hasNext()
    }

    fun hasPrevEpisode(): Boolean? {
        return generator?.hasPrev()
    }

    fun preLoadNextLinks() {
        val id = getId()
        // Do not preload if already loading
        if (id == currentLoadingEpisodeId) return

        Log.i(TAG, "preLoadNextLinks")
        currentJob?.cancel()
        currentLoadingEpisodeId = id

        currentJob = viewModelScope.launch {
            try {
                if (generator?.hasCache == true && generator?.hasNext() == true) {
                    safeApiCall {
                        generator?.generateLinks(
                            sourceTypes = LOADTYPE_INAPP,
                            clearCache = false,
                            callback = {},
                            subtitleCallback = {},
                            offset = 1
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

    fun getLoadResponse(): LoadResponse? {
        return safe { (generator as? RepoLinkGenerator?)?.page }
    }

    fun getMeta(): Any? {
        return safe { generator?.getCurrent() }
    }

    fun getAllMeta(): List<Any>? {
        return safe { generator?.getAll() }
    }

    fun getNextMeta(): Any? {
        return safe {
            if (generator?.hasNext() == false) return@safe null
            generator?.getCurrent(offset = 1)
        }
    }

    fun loadThisEpisode(index:Int) {
        generator?.goto(index)
        loadLinks()
    }

    fun getCurrentIndex():Int?{
        val repoGen = generator as? RepoLinkGenerator ?: return null
        return repoGen.videoIndex
    }

    fun attachGenerator(newGenerator: IGenerator?) {
        if (generator == null) {
            generator = newGenerator
        }
    }

    private var extraSubtitles : MutableSet<SubtitleData> = mutableSetOf()

    /**
     * If duplicate nothing will happen
     * */
    fun addSubtitles(file: Set<SubtitleData>) = synchronized(extraSubtitles) {
        extraSubtitles += file
        val current = _currentSubs.value ?: emptySet()
        val next = extraSubtitles + current

        // if it is of a different size then we have added distinct items
        if (next.size != current.size) {
            // Posting will refresh subtitles which will in turn
            // make the subs to english if previously unselected
            _currentSubs.postValue(next)
        }
    }

    private var currentJob: Job? = null
    private var currentStampJob: Job? = null

    fun loadStamps(duration: Long) {
        //currentStampJob?.cancel()
        currentStampJob = ioSafe {
            val meta = generator?.getCurrent()
            val page = (generator as? RepoLinkGenerator?)?.page
            if (page != null && meta is ResultEpisode) {
                _currentStamps.postValue(listOf())
                _currentStamps.postValue(
                    EpisodeSkip.getStamps(
                        page,
                        meta,
                        duration,
                        hasNextEpisode() ?: false
                    )
                )
            }
        }
    }

    fun loadLinks(sourceTypes: Set<ExtractorLinkType> = LOADTYPE_INAPP) {
        Log.i(TAG, "loadLinks")
        currentJob?.cancel()

        currentJob = viewModelScope.launchSafe {
            // if we load links then we clear the prev loaded links
            synchronized(extraSubtitles) {
                extraSubtitles.clear()
            }
            val currentLinks = mutableSetOf<Pair<ExtractorLink?, ExtractorUri?>>()
            val currentSubs = mutableSetOf<SubtitleData>()

            // clear old data
            _currentSubs.postValue(emptySet())
            _currentLinks.postValue(emptySet())

            // load more data
            _loadingLinks.postValue(Resource.Loading())
            val loadingState = safeApiCall {
                generator?.generateLinks(
                    sourceTypes = sourceTypes,
                    clearCache = forceClearCache,
                    callback = {
                        synchronized(currentLinks) {
                            currentLinks.add(it)
                            // Clone to prevent ConcurrentModificationException
                            safe {
                                // Extra safe since .toSet() iterates.
                                _currentLinks.postValue(currentLinks.toSet())
                            }
                        }
                    },
                    subtitleCallback = {
                        synchronized(extraSubtitles) {
                            currentSubs.add(it)
                            safe {
                                _currentSubs.postValue(currentSubs + extraSubtitles)
                            }
                        }
                    })
            }

            _loadingLinks.postValue(loadingState)
            _currentLinks.postValue(currentLinks)
            synchronized(extraSubtitles) {
                _currentSubs.postValue(currentSubs + extraSubtitles)
            }
        }

    }
}