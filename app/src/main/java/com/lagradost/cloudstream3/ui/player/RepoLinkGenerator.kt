package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import kotlin.math.max
import kotlin.math.min

class RepoLinkGenerator(
    private val episodes: List<ResultEpisode>,
    private var currentIndex: Int = 0,
    val page: LoadResponse? = null,
) : IGenerator {
    companion object {
        const val TAG = "RepoLink"
        val cache: HashMap<Pair<String, Int>, Pair<MutableSet<ExtractorLink>, MutableSet<SubtitleData>>> =
            hashMapOf()
    }

    override val hasCache = true

    override fun hasNext(): Boolean {
        return currentIndex < episodes.size - 1
    }

    override fun hasPrev(): Boolean {
        return currentIndex > 0
    }

    override fun next() {
        Log.i(TAG, "next")
        if (hasNext())
            currentIndex++
    }

    override fun prev() {
        Log.i(TAG, "prev")
        if (hasPrev())
            currentIndex--
    }

    override fun goto(index: Int) {
        Log.i(TAG, "goto $index")
        // clamps value
        currentIndex = min(episodes.size - 1, max(0, index))
    }

    override fun getCurrentId(): Int {
        return episodes[currentIndex].id
    }

    override fun getCurrent(offset: Int): Any? {
        return episodes.getOrNull(currentIndex + offset)
    }

    override fun getAll(): List<Any> {
        return episodes
    }

    // this is a simple array that is used to instantly load links if they are already loaded
    //var linkCache = Array<Set<ExtractorLink>>(size = episodes.size, init = { setOf() })
    //var subsCache = Array<Set<SubtitleData>>(size = episodes.size, init = { setOf() })

    override suspend fun generateLinks(
        clearCache: Boolean,
        isCasting: Boolean,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
    ): Boolean {
        val index = currentIndex
        val current = episodes.getOrNull(index + offset) ?: return false

        val (currentLinkCache, currentSubsCache) = if (clearCache) {
            Pair(mutableSetOf(), mutableSetOf())
        } else {
            cache[Pair(current.apiName, current.id)] ?: Pair(mutableSetOf(), mutableSetOf())
        }

        //val currentLinkCache = if (clearCache) mutableSetOf() else linkCache[index].toMutableSet()
        //val currentSubsCache = if (clearCache) mutableSetOf() else subsCache[index].toMutableSet()

        val currentLinks = mutableSetOf<String>()       // makes all urls unique
        val currentSubsUrls = mutableSetOf<String>()    // makes all subs urls unique
        val currentSubsNames = mutableSetOf<String>()   // makes all subs names unique

        currentLinkCache.forEach { link ->
            currentLinks.add(link.url)
            callback(Pair(link, null))
        }

        currentSubsCache.forEach { sub ->
            currentSubsUrls.add(sub.url)
            currentSubsNames.add(sub.name)
            subtitleCallback(sub)
        }

        // this stops all execution if links are cached
        // no extra get requests
        if (currentLinkCache.size > 0) {
            return true
        }

        val result = APIRepository(
            getApiFromNameNull(current.apiName) ?: throw Exception("This provider does not exist")
        ).loadLinks(current.data,
            isCasting,
            { file ->
                val correctFile = PlayerSubtitleHelper.getSubtitleData(file)
                if (!currentSubsUrls.contains(correctFile.url)) {
                    currentSubsUrls.add(correctFile.url)

                    // this part makes sure that all names are unique for UX
                    var name = correctFile.name
                    var count = 0
                    while (currentSubsNames.contains(name)) {
                        count++
                        name = "${correctFile.name} $count"
                    }

                    currentSubsNames.add(name)
                    val updatedFile = correctFile.copy(name = name)

                    if (!currentSubsCache.contains(updatedFile)) {
                        subtitleCallback(updatedFile)
                        currentSubsCache.add(updatedFile)
                        //subsCache[index] = currentSubsCache
                    }
                }
            },
            { link ->
                Log.d(TAG, "Loaded ExtractorLink: $link")
                if (!currentLinks.contains(link.url)) {
                    if (!currentLinkCache.contains(link)) {
                        currentLinks.add(link.url)
                        callback(Pair(link, null))
                        currentLinkCache.add(link)
                        //linkCache[index] = currentLinkCache
                    }
                }
            }
        )
        cache[Pair(current.apiName, current.id)] = Pair(currentLinkCache, currentSubsCache)

        return result
    }
}