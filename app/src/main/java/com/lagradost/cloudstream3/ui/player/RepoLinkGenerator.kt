package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.math.max
import kotlin.math.min

data class Cache(
    val linkCache: MutableSet<ExtractorLink>,
    val subtitleCache: MutableSet<SubtitleData>,
    var lastCachedTimestamp: Long = unixTime
)

class RepoLinkGenerator(
    private val episodes: List<ResultEpisode>,
    private var currentIndex: Int = 0,
    val page: LoadResponse? = null,
) : IGenerator {
    companion object {
        const val TAG = "RepoLink"
        val cache: HashMap<Pair<String, Int>, Cache> =
            hashMapOf()
    }

    override val hasCache = true
    override val canSkipLoading = true

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
        allowedTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val index = currentIndex
        val current = episodes.getOrNull(index + offset) ?: return false

        val (currentLinkCache, currentSubsCache, lastCachedTimestamp) = if (clearCache) {
            Cache(mutableSetOf(), mutableSetOf(), unixTime)
        } else {
            cache[current.apiName to current.id] ?: Cache(mutableSetOf(), mutableSetOf(), unixTime)
        }

        //val currentLinkCache = if (clearCache) mutableSetOf() else linkCache[index].toMutableSet()
        //val currentSubsCache = if (clearCache) mutableSetOf() else subsCache[index].toMutableSet()

        val currentLinks = mutableSetOf<String>()       // makes all urls unique
        val currentSubsUrls = mutableSetOf<String>()    // makes all subs urls unique
        val currentSubsNames = mutableSetOf<String>()   // makes all subs names unique

        val invalidateCache = unixTime - lastCachedTimestamp  > 60 * 20 // 20 minutes
        if(invalidateCache){
            currentLinkCache.clear()
            currentSubsCache.clear()
        }

        currentLinkCache.filter { allowedTypes.contains(it.type) }.forEach { link ->
            currentLinks.add(link.url)
            callback(link to null)
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
            isCasting = isCasting,
            subtitleCallback = { file ->
                val correctFile = PlayerSubtitleHelper.getSubtitleData(file)
                if (correctFile.url.isNotEmpty() && !currentSubsUrls.contains(correctFile.url)) {
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
            callback = { link ->
                Log.d(TAG, "Loaded ExtractorLink: $link")
                if (link.url.isNotEmpty() && !currentLinks.contains(link.url) && !currentLinkCache.contains(link)) {
                    currentLinks.add(link.url)

                    if (allowedTypes.contains(link.type)) {
                        callback(Pair(link, null))
                    }

                    currentLinkCache.add(link)
                    // linkCache[index] = currentLinkCache
                }
            }
        )
        cache[Pair(current.apiName, current.id)] = Cache(currentLinkCache, currentSubsCache, unixTime)

        return result
    }
}