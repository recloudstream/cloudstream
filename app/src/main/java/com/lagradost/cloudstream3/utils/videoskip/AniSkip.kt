package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// taken from https://github.com/saikou-app/saikou/blob/3803f8a7a59b826ca193664d46af3a22bbc989f7/app/src/main/java/ani/saikou/others/AniSkip.kt
// the following is GPLv3 code https://github.com/saikou-app/saikou/blob/main/LICENSE.md
class AniSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA)

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        if (data !is AnimeLoadResponse) return null // Filter actual anime

        val malId = data.getMalId()?.toIntOrNull() ?: return null
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/${episode.episode}?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=${episodeDurationMs / 1000L}"

        val response = app.get(url).parsed<AniSkipResponse>()

        // because it also returns an expected episode length we use that just in case it is mismatched with like 2s next episode will still work
        return response.results?.mapNotNull { stamp ->
            val skipType = when (stamp.skipType) {
                "op" -> SkipType.Opening
                "ed" -> SkipType.Ending
                "recap" -> SkipType.Recap
                "mixed-ed" -> SkipType.MixedEnding
                "mixed-op" -> SkipType.MixedOpening
                else -> null
            } ?: return@mapNotNull null
            val end = (stamp.interval.endTime * 1000.0).toLong()
            val start = (stamp.interval.startTime * 1000.0).toLong()
            SkipStamp(
                type = skipType,
                startMs = start,
                endMs = end,
            )
        }
    }

    @Serializable
    data class AniSkipResponse(
        @SerialName("found") val found: Boolean,
        @SerialName("results") val results: List<Stamp>?,
        @SerialName("message") val message: String?,
        @SerialName("statusCode") val statusCode: Int,
    )

    @Serializable
    data class Stamp(
        @SerialName("interval") val interval: AniSkipInterval,
        @SerialName("skipType") val skipType: String,
        @SerialName("skipId") val skipId: String,
        @SerialName("episodeLength") val episodeLength: Double,
    )

    @Serializable
    data class AniSkipInterval(
        @SerialName("startTime") val startTime: Double,
        @SerialName("endTime") val endTime: Double,
    )
}