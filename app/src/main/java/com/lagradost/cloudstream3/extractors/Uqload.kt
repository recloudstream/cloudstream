package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app

class Uqload1 : Uqload() {
    override var mainUrl = "https://uqload.com"
}

open class Uqload : ExtractorApi() {
    override val name: String = "Uqload"
    override val mainUrl: String = "https://www.uqload.com"
    private val srcRegex = Regex("""sources:.\[(.*?)\]""")  // would be possible to use the parse and find src attribute
    override val requiresReferer = true


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val lang = url.substring(0, 2)
        val flag =
            if (lang == "vo") {
                " \uD83C\uDDEC\uD83C\uDDE7"
            }
            else if (lang == "vf"){
                " \uD83C\uDDE8\uD83C\uDDF5"
            } else {
                ""
            }
        
        val cleaned_url = if (lang == "ht") {  // if url doesn't contain a flag and the url starts with http://
            url
        } else {
            url.substring(2, url.length)
        }
        with(app.get(cleaned_url)) {  // raised error ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) is due to the response: "error_nofile"
            srcRegex.find(this.text)?.groupValues?.get(1)?.replace("\"", "")?.let { link ->
                return listOf(
                    ExtractorLink(
                        name,
                        name + flag,
                        link,
                        cleaned_url,
                        Qualities.Unknown.value,
                    )
                )
            }
        }
        return null
    }
}
