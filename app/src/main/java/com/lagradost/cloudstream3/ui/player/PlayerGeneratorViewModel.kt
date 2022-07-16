package com.lagradost.cloudstream3.ui.player

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerGeneratorViewModel : ViewModel() {
    companion object {
        val TAG = "PlayViewGen"
    }

    private var generator: IGenerator? = null

    private val _currentLinks = MutableLiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>>(setOf())
    val currentLinks: LiveData<Set<Pair<ExtractorLink?, ExtractorUri?>>> = _currentLinks

    private val _currentSubs = MutableLiveData<Set<SubtitleData>>(setOf())
    val currentSubs: LiveData<Set<SubtitleData>> = _currentSubs

    private val _loadingLinks = MutableLiveData<Resource<Boolean?>>()
    val loadingLinks: LiveData<Resource<Boolean?>> = _loadingLinks

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

    fun preLoadNextLinks() {
        Log.i(TAG, "preLoadNextLinks")
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            if (generator?.hasCache == true && generator?.hasNext() == true) {
                safeApiCall {
                    generator?.generateLinks(
                        clearCache = false,
                        isCasting = false,
                        {},
                        {},
                        offset = 1
                    )
                }
            }
        }
    }

    fun getMeta(): Any? {
        return normalSafeApiCall { generator?.getCurrent() }
    }

    fun getAllMeta(): List<Any>? {
        return normalSafeApiCall { generator?.getAll() }
    }

    fun getNextMeta(): Any? {
        return normalSafeApiCall {
            if (generator?.hasNext() == false) return@normalSafeApiCall null
            generator?.getCurrent(offset = 1)
        }
    }

    fun attachGenerator(newGenerator: IGenerator?) {
        if (generator == null) {
            generator = newGenerator
        }
    }

    /**
     * If duplicate nothing will happen
     * */
    fun addSubtitles(file: Set<SubtitleData>) {
        val currentSubs = _currentSubs.value ?: emptySet()
        // Prevent duplicates
        val allSubs = (currentSubs + file).distinct().toSet()
        // Do not post if there's nothing new
        // Posting will refresh subtitles which will in turn
        // make the subs to english if previously unselected
        if (allSubs != currentSubs)
            _currentSubs.postValue(allSubs)
    }

    private var currentJob: Job? = null

    fun loadLinks(clearCache: Boolean = false, isCasting: Boolean = false) {
        Log.i(TAG, "loadLinks")
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val currentLinks = mutableSetOf<Pair<ExtractorLink?, ExtractorUri?>>()
            val currentSubs = mutableSetOf<SubtitleData>()

            // clear old data
            _currentSubs.postValue(currentSubs)
            _currentLinks.postValue(currentLinks)

            // load more data
            _loadingLinks.postValue(Resource.Loading())
            val loadingState = safeApiCall {
                generator?.generateLinks(clearCache = clearCache, isCasting = isCasting, {
                    currentLinks.add(it)
                    _currentLinks.postValue(currentLinks)
                }, {
                    currentSubs.add(it)
                    // _currentSubs.postValue(currentSubs) // this causes ConcurrentModificationException, so fuck it
                })
            }

            _loadingLinks.postValue(loadingState)

            _currentLinks.postValue(currentLinks)
            _currentSubs.postValue(currentSubs)
        }
    }
}