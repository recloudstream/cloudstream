package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class StreamTape : ExtractorApi() {
    override val name: String = "StreamTape"
    override val mainUrl: String = "https://streamtape.com"
    override val requiresReferer = false

    // Because they add concatenation to fuck up scrapers, DON'T LET LAG CODE ANYTHING
    private val linkRegex =
        Regex("""(i(|" \+ ')d(|" \+ ')=.*?&(|" \+ ')e(|" \+ ')x(|" \+ ')p(|" \+ ')i(|" \+ ')r(|" \+ ')e(|" \+ ')s(|" \+ ')=.*?&(|" \+ ')i(|" \+ ')p(|" \+ ')=.*?&(|" \+ ')t(|" \+ ')o(|" \+ ')k(|" \+ ')e(|" \+ ')n(|" \+ ')=.*)'""")

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(khttp.get(url)) {
            linkRegex.find(this.text)?.let {
                val extractedUrl = "https://streamtape.com/get_video?${it.groupValues[1]}".replace("""" + '""", "")
                return listOf(
                    ExtractorLink(
                        name,
                        name,
                        extractedUrl,
                        url,
                        Qualities.Unknown.value,
                    )
                )
            }
        }
        return null
    }
}