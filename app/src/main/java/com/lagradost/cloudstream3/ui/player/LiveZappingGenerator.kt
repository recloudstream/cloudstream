package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.buildResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.ConcurrentHashMap

private data class LiveZappingChannel(
    val name: String,
    val url: String,
    val apiName: String,
    val poster: String?,
)

private fun liveEpisode(index: Int, channel: LiveZappingChannel): ResultEpisode = buildResultEpisode(
    headerName = channel.name,
    name = channel.name,
    poster = channel.poster,
    episode = index,
    data = channel.url,
    apiName = channel.apiName,
    id = "${channel.apiName}:${channel.url}".hashCode(),
    index = index,
    tvType = TvType.Live,
    parentId = "${channel.apiName}:${channel.url}".hashCode(),
)

/**
 * A normal player generator whose entries are the live channels from one Home row.
 * The player can therefore use its existing episode list and episode index handling.
 */
class LiveZappingGenerator private constructor(
    channels: List<LiveZappingChannel>,
) : VideoGenerator<ResultEpisode>(channels.mapIndexed(::liveEpisode)) {
    companion object {
        private val pending = ConcurrentHashMap<String, List<LiveZappingChannel>>()

        private fun key(url: String, apiName: String) = "$apiName\n$url"

        fun remember(response: SearchResponse, channels: List<SearchResponse>) {
            pending[key(response.url, response.apiName)] = channels.map {
                LiveZappingChannel(it.name, it.url, it.apiName, it.posterUrl)
            }
        }

        fun take(url: String, apiName: String, name: String?): Pair<LiveZappingGenerator, Int>? {
            val channels = pending.remove(key(url, apiName)) ?: return null
            if (channels.isEmpty()) return null
            val index = channels.indexOfFirst { it.url == url && it.apiName == apiName && (name == null || it.name == name) }
                .takeIf { it >= 0 }
                ?: channels.indexOfFirst { it.url == url && it.apiName == apiName }
            if (index < 0) return null
            return LiveZappingGenerator(channels) to index
        }

        fun discard(url: String, apiName: String) {
            pending.remove(key(url, apiName))
        }
    }

    override val hasCache: Boolean = false
    override val canSkipLoading: Boolean = false
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val episode = videos.getOrNull(offset) ?: return false
        val api = getApiFromNameNull(episode.apiName) ?: return false
        val loaded = APIRepository(api).load(episode.data)
        val live = (loaded as? Resource.Success)?.value as? LiveStreamLoadResponse ?: return false
        return APIRepository(api).loadLinks(
            live.dataUrl,
            isCasting,
            subtitleCallback = { subtitleCallback(PlayerSubtitleHelper.getSubtitleData(it)) },
            callback = { link -> callback(link to null) },
        )
    }
}
