package com.lagradost.cloudstream3.ui.settings

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.mapper

object ScoreManager {
    private const val mainUrl = "http://dreamlo.com"
    private const val publicCode = "612d3dcf8f40bb6e98bece15"
     var privateCode: String? = BuildConfig.PRIVATE_BENENE_KEY // plz keep it a bit fair

    data class DreamloMain(
        @JsonProperty("dreamlo") var dreamlo: Dreamlo
    )

    data class Dreamlo(
        @JsonProperty("leaderboard") var leaderboard: Leaderboard
    )

    data class Leaderboard(
        @JsonProperty("entry") var entry: List<DreamloEntry>
    )

    data class DreamloEntry(
        @JsonProperty("name") var name: String,
        @JsonProperty("score") var score: String,
        //@JsonProperty("seconds") var seconds: String,
        //@JsonProperty("text") var text: String,
        // @JsonProperty("date") var date: String
    )

    fun getScore(): List<DreamloEntry> {
        val response = khttp.get("$mainUrl/lb/$publicCode/json")

        return mapper.readValue<DreamloMain>(response.text).dreamlo.leaderboard.entry
    }

    fun addScore(name: String, score: Int) { // plz dont cheat
        if(score < 0 || score > 100000 || privateCode.isNullOrBlank()) return
        khttp.get("$mainUrl/lb/$privateCode/add/$name/$score")
    }
}