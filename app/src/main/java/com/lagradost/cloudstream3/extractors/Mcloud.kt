package com.lagradost.cloudstream3.extractors

open class Mcloud : WcoStream() {
    override var name = "Mcloud"
    override var mainUrl = "https://mcloud.to"
    override val requiresReferer = true
}