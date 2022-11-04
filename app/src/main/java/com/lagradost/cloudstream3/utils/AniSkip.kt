package com.lagradost.cloudstream3.utils

import android.util.Log
import androidx.annotation.StringRes
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.txt

object EpisodeSkip {
    data class SkipStamp(
        @StringRes
        private val name: Int,
        val startMs: Long,
        val endMs: Long,
    ) {
        val uiText = txt(R.string.skip_type_format, txt(name))
    }

    private val cachedStamps = HashMap<Int, List<SkipStamp>>()

    suspend fun getStamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp> {
        cachedStamps[episode.id]?.let { list ->
            return list
        }

        val out = mutableListOf<SkipStamp>()
        println("CALLING WITH : ${data.syncData} $episode $episodeDurationMs")
        if (data is AnimeLoadResponse && (data.type == TvType.Anime || data.type == TvType.OVA)) {
            data.getMalId()?.toIntOrNull()?.let { malId ->
                AniSkip.getResult(malId, episode.episode, episodeDurationMs)?.mapNotNull { stamp ->
                    val name = when (stamp.skipType) {
                        "op" -> R.string.skip_type_op
                        "ed" -> R.string.skip_type_ed
                        "recap" -> R.string.skip_type_recap
                        "mixed-ed" -> R.string.skip_type_mixed_ed
                        "mixed-op" -> R.string.skip_type_mixed_op
                        else -> null
                    } ?: return@mapNotNull null
                    SkipStamp(
                        name,
                        (stamp.interval.startTime * 1000.0).toLong(),
                        (stamp.interval.endTime * 1000.0).toLong()
                    )
                }?.let { list ->
                    out.addAll(list)
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
    suspend fun getResult(malId: Int, episodeNumber: Int, episodeLength: Long): List<Stamp>? {
        return try {
            val url =
                "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=${episodeLength / 1000L}"
            println("URLLLL::::$url")

            val a = app.get(url)
            println("GOT RESPONSE:::.")
            val res = a.parsed<AniSkipResponse>()
            Log.i("AniSkip", "Response = $res")
            if (res.found) res.results else null
        } catch (t: Throwable) {
            Log.i("AniSkip", "error = ${t.message}")
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


    //fun String.getType(): String {
    //
    //
    //
    //
    //
    //
    //
    //
    //}

    data class AniSkipInterval(
        @JsonSerialize val startTime: Double,
        @JsonSerialize val endTime: Double
    )
}