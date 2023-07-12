import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.select.Elements
import java.util.regex.Matcher
import java.util.regex.Pattern

class StreamoUpload : StreamoUpload() {
    override val mainUrl = "https://streamoupload.xyz"
}

open class StreamoUpload : ExtractorApi(name = "StreamoUpload") {
    override val mainUrl = "https://streamoupload.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val response = app.get(url, referer = referer)
        val scriptElements: Elements = response.document.select("script")
        val scriptPattern: Pattern = Pattern.compile("jwplayer\\(\"vplayer\"\\)\\.setup\\((.*?)\\);", Pattern.DOTALL)
        val filePattern: Pattern = Pattern.compile("\"file\"\\s*:\\s*\"(.*?)\"")

        for (script in scriptElements) {
            if (script.data().contains("jwplayer(\"vplayer\").setup(")) {
                val dataMatcher: Matcher = scriptPattern.matcher(script.data())
                if (dataMatcher.find()) {
                    val data = dataMatcher.group(1)
                    val fileMatcher: Matcher = filePattern.matcher(data)

                    while (fileMatcher.find()) {
                        val fileUrl = fileMatcher.group(1)
                        val videoUrl = "$mainUrl$fileUrl"
                        sources.add(ExtractorLink(videoUrl))
                    }
                }
            }
        }

        return sources
    }

    private data class File(
        @JsonProperty("file") val file: String,
    )
}

