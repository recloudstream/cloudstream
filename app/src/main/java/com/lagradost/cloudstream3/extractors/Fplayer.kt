package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.Session
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper

class Fplayer: XStreamCdn() {
    override val name: String = "Fplayer"
    override val mainUrl: String = "https://fplayer.info"
}
