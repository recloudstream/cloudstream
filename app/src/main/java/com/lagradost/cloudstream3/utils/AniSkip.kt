package com.lagradost.cloudstream3.utils

import android.util.Log
import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import java.lang.Long.min

object EpisodeSkip {
    private const val TAG = "EpisodeSkip"

    enum class SkipType(@StringRes name: Int) {
        Opening(R.string.skip_type_op),
        Ending(R.string.skip_type_ed),
        Recap(R.string.skip_type_recap),
        MixedOpening(R.string.skip_type_mixed_op),
        MixedEnding(R.string.skip_type_mixed_ed),
        Credits(R.string.skip_type_creddits),
        Intro(R.string.skip_type_creddits),
    }

    data class SkipStamp(
        val type: SkipType,
        val skipToNextEpisode: Boolean,
        val startMs: Long,
        val endMs: Long,
    ) {
        val uiText = if (skipToNextEpisode) txt(R.string.next_episode) else txt(
            R.string.skip_type_format,
            txt(type.name)
        )
    }

    private val cachedStamps = HashMap<Int, List<SkipStamp>>()

    private fun shouldSkipToNextEpisode(endMs: Long, episodeDurationMs: Long): Boolean {
        return episodeDurationMs - endMs < 20_000L // some might have outro that we don't care about tbh
    }

    suspend fun getStamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long,
        hasNextEpisode: Boolean,
    ): List<SkipStamp> {
        cachedStamps[episode.id]?.let { list ->
            return list
        }

        val out = mutableListOf<SkipStamp>()
        Log.i(TAG, "Requesting SkipStamp from ${data.syncData}")

        if (data is AnimeLoadResponse && (data.type == TvType.Anime || data.type == TvType.OVA)) {
            data.getMalId()?.toIntOrNull()?.let { malId ->
                val (resultLength, stamps) = AniSkip.getResult(
                    malId,
                    episode.episode,
                    episodeDurationMs
                ) ?: return@let null
                // because it also returns an expected episode length we use that just in case it is mismatched with like 2s next episode will still work
                val dur = min(episodeDurationMs, resultLength)
                stamps.mapNotNull { stamp ->
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
                        skipToNextEpisode = hasNextEpisode && shouldSkipToNextEpisode(
                            end,
                            dur
                        ),
                        startMs = start,
                        endMs = end
                    )
                }.let { list ->
                    out.addAll(list)
                }
            }
        } else if (data.type == TvType.TvSeries) {
            val season = episode.season
            val imdbId = data.getImdbId()

            if (season != null && imdbId != null) {
                val result = IntroDbSkip.getResult(
                    imdbId,
                    season,
                    episode.episode
                )

                result?.let { res ->
                    listOfNotNull(
                        res.intro?.let {
                            val start = it.startMs ?: return@let null
                            val end = it.endMs ?: return@let null
                            SkipStamp(
                                type = SkipType.Opening,
                                skipToNextEpisode = hasNextEpisode &&
                                        shouldSkipToNextEpisode(end, episodeDurationMs),
                                startMs = start,
                                endMs = end
                            )
                        },
                        res.recap?.let {
                            val start = it.startMs ?: return@let null
                            val end = it.endMs ?: return@let null
                            SkipStamp(
                                type = SkipType.Recap,
                                skipToNextEpisode = hasNextEpisode &&
                                        shouldSkipToNextEpisode(end, episodeDurationMs),
                                startMs = start,
                                endMs = end
                            )
                        },
                        res.outro?.let {
                            val start = it.startMs ?: return@let null
                            val end = it.endMs ?: return@let null
                            SkipStamp(
                                type = SkipType.Credits,
                                skipToNextEpisode = hasNextEpisode &&
                                        shouldSkipToNextEpisode(end, episodeDurationMs),
                                startMs = start,
                                endMs = end
                            )
                        }
                    ).let { out.addAll(it) }
                }
            }
        }

        if (out.isNotEmpty())
            cachedStamps[episode.id] = out
        return out
    }
}

// taken from https://github.com/saikou-app/saikou/blob/3803f8a7a59b826ca193664d46af3a22bbc989f7/app/src/main/java/ani/saikou/others/AniSkip.kt
// the following is GPLv3 code https://github.com/saikou-app/saikou/blob/main/LICENSE.md
object AniSkip {
    private const val TAG = "AniSkip"
    suspend fun getResult(
        malId: Int,
        episodeNumber: Int,
        episodeLength: Long
    ): Pair<Long, List<Stamp>>? {
        return try {
            val url =
                "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=${episodeLength / 1000L}"
            Log.i(TAG, "Requesting $url")

            val a = app.get(url)
            val res = a.parsed<AniSkipResponse>()
            Log.i(TAG, "Found ${res.found} with ${res.results?.size} results")
            if (res.found && !res.results.isNullOrEmpty()) (res.results[0].episodeLength * 1000).toLong() to res.results else null
        } catch (t: Throwable) {
            Log.i(TAG, "error = ${t.message}")
            logError(t)
            null
        }
    }

    data class AniSkipResponse(
        @JsonSerialize val found: Boolean,
        @JsonSerialize val results: List<Stamp>?,
        @JsonSerialize val message: String?,
        @JsonSerialize val statusCode: Int
    )

    data class Stamp(
        @JsonSerialize val interval: AniSkipInterval,
        @JsonSerialize val skipType: String,
        @JsonSerialize val skipId: String,
        @JsonSerialize val episodeLength: Double
    )

    data class AniSkipInterval(
        @JsonSerialize val startTime: Double,
        @JsonSerialize val endTime: Double
    )
}

object IntroDbSkip {
    private const val TAG = "IntroDb"

    suspend fun getResult(
        imdbId: String,
        season: Int,
        episode: Int,
    ): IntroDbResponse? {
        return try {
            val url =
                "https://api.introdb.app/segments?imdb_id=$imdbId&season=$season&episode=$episode"
            app.get(url).parsed<IntroDbResponse>()
        } catch (t: Throwable) {
            Log.i(TAG, "error = ${t.message}")
            logError(t)
            null
        }
    }

    data class IntroDbResponse(
        @JsonProperty("imdb_id") val imdbId: String?,
        val season: Int?,
        val episode: Int?,
        val intro: Segment?,
        val recap: Segment?,
        val outro: Segment?,
    )

    data class Segment(
        @JsonProperty("start_sec") val startSec: Double?,
        @JsonProperty("end_sec") val endSec: Double?,
        @JsonProperty("start_ms") val startMs: Long?,
        @JsonProperty("end_ms") val endMs: Long?,
        val confidence: Double?,
        @JsonProperty("submission_count") val submissionCount: Int?,
        @JsonProperty("updated_at") val updatedAt: String?,
    )
}