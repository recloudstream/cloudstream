package com.lagradost.cloudstream3.extractors

open class VidhideExtractor : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhide.com"
    override val requiresReferer = false
}