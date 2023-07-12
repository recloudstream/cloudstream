import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import java.util.regex.Matcher
import java.util.regex.Pattern

class StreamoUpload1 : StreamoUpload() {
    override val mainUrl = "https://streamoupload.xyz"
}

open class StreamoUpload : ExtractorApi() {
    override val name = "StreamoUpload"
    override val mainUrl = "https://streamoupload.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val response = app.get(url, referer = referer)
        val scriptPattern: Pattern = Pattern.compile("jwplayer\\(\"vplayer\"\\)\\.setup\\((.*?)\\);", Pattern.DOTALL)
        val filePattern: Pattern = Pattern.compile("\"file\"\\s*:\\s*\"(.*?)\"")

        val scriptMatcher: Matcher = scriptPattern.matcher(response.body)

        while (scriptMatcher.find()) {
            val scriptData = scriptMatcher.group(1)
            val fileMatcher: Matcher = filePattern.matcher(scriptData)

            while (fileMatcher.find()) {
                val fileUrl = fileMatcher.group(1)
                val videoUrl = "$mainUrl$fileUrl"
                sources.add(ExtractorLink(videoUrl))
            }
        }

        return sources
    }

    private data class File(
        @JsonProperty("file") val file: String,
    )
}
