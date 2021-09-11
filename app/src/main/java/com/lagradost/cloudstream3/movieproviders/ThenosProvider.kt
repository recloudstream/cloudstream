import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class ThenosProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://www.thenos.org"
    override val name: String
        get() = "Thenos"

    override val hasQuickSearch: Boolean
        get() = true

    override val hasMainPage: Boolean
        get() = true

    override val hasChromecastSupport: Boolean
        get() = false

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
        )

    override val vpnStatus: VPNStatus
        get() = VPNStatus.None

    override fun getMainPage(): HomePageResponse? {
        val map = mapOf(
            "New Releases" to "released",
            "Recently Added in Movies" to "recent",
            "Recently Added in Shows" to "recent/shows",
            "Top Rated" to "rating"
        )
        val list = ArrayList<HomePageList>()
        map.entries.forEach {
            val url = "$apiUrl/library/${it.value}"
            val response = khttp.get(url)
            val mapped = mapper.readValue<ThenosLoadResponse>(response.text)

            mapped.Metadata?.mapNotNull {
                it?.toSearchResponse()
            }?.let { searchResponses ->
                list.add(
                    HomePageList(
                        it.key,
                        searchResponses
                    )
                )
            }
        }

        return HomePageResponse(
            list
        )
    }

    fun secondsToReadable(seconds: Int, completedValue: String): String {
        var secondsLong = seconds.toLong()
        val days = TimeUnit.SECONDS
            .toDays(secondsLong)
        secondsLong -= TimeUnit.DAYS.toSeconds(days)

        val hours = TimeUnit.SECONDS
            .toHours(secondsLong)
        secondsLong -= TimeUnit.HOURS.toSeconds(hours)

        val minutes = TimeUnit.SECONDS
            .toMinutes(secondsLong)
        secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
        if (minutes < 0) {
            return completedValue
        }
        //println("$days $hours $minutes")
        return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
    }

    private val apiUrl = "https://api.thenos.org"

    override fun quickSearch(query: String): List<SearchResponse> {
        val url = "$apiUrl/library/search?query=$query"
        return searchFromUrl(url)
    }

    data class ThenosSearchResponse(
        @JsonProperty("size") val size: Int?,
        @JsonProperty("Hub") val Hub: List<Hub>?
    )

    data class Part(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("duration") val duration: Long?,
        @JsonProperty("file") val file: String?,
        @JsonProperty("size") val size: Long?,
        @JsonProperty("audioProfile") val audioProfile: String?,
        @JsonProperty("container") val container: String?,
        @JsonProperty("has64bitOffsets") val has64bitOffsets: Boolean?,
        @JsonProperty("optimizedForStreaming") val optimizedForStreaming: Boolean?,
        @JsonProperty("videoProfile") val videoProfile: String?
    )

    data class Media(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("duration") val duration: Long?,
        @JsonProperty("bitrate") val bitrate: Long?,
        @JsonProperty("width") val width: Long?,
        @JsonProperty("height") val height: Long?,
        @JsonProperty("aspectRatio") val aspectRatio: Double?,
        @JsonProperty("audioChannels") val audioChannels: Long?,
        @JsonProperty("audioCodec") val audioCodec: String?,
        @JsonProperty("videoCodec") val videoCodec: String?,
        @JsonProperty("videoResolution") val videoResolution: String?,
        @JsonProperty("container") val container: String?,
        @JsonProperty("videoFrameRate") val videoFrameRate: String?,
        @JsonProperty("optimizedForStreaming") val optimizedForStreaming: Long?,
        @JsonProperty("audioProfile") val audioProfile: String?,
        @JsonProperty("has64bitOffsets") val has64bitOffsets: Boolean?,
        @JsonProperty("videoProfile") val videoProfile: String?,
        @JsonProperty("Part") val Part: List<Part>?
    )

    data class Genre(
        @JsonProperty("tag") val tag: String?
    )


    data class Country(
        @JsonProperty("tag") val tag: String?
    )


    data class Role(
        @JsonProperty("tag") val tag: String?
    )

    data class Hub(
        @JsonProperty("title") val title: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("hubIdentifier") val hubIdentifier: String?,
        @JsonProperty("context") val context: String?,
        @JsonProperty("size") val size: Int?,
        @JsonProperty("more") val more: Boolean?,
        @JsonProperty("style") val style: String?,
        @JsonProperty("Metadata") val Metadata: List<Metadata>?
    )

    data class Metadata(
        @JsonProperty("librarySectionTitle") val librarySectionTitle: String?,
        @JsonProperty("ratingKey") val ratingKey: String?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("guid") val guid: String?,
        @JsonProperty("studio") val studio: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("librarySectionID") val librarySectionID: Int?,
        @JsonProperty("librarySectionKey") val librarySectionKey: String?,
        @JsonProperty("contentRating") val contentRating: String?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("audienceRating") val audienceRating: Int?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("art") val art: String?,
        @JsonProperty("duration") val duration: Int?,
        @JsonProperty("originallyAvailableAt") val originallyAvailableAt: String?,
        @JsonProperty("addedAt") val addedAt: Int?,
        @JsonProperty("updatedAt") val updatedAt: Int?,
        @JsonProperty("audienceRatingImage") val audienceRatingImage: String?,
        @JsonProperty("Media") val Media: List<Media>?,
        @JsonProperty("Genre") val Genre: List<Genre>?,
        @JsonProperty("Director") val Director: List<Director>?,
        @JsonProperty("Country") val Country: List<Country>?,
        @JsonProperty("Role") val Role: List<Role>?
    )

    data class Director(
        @JsonProperty("tag") val tag: String
    )

    private fun Metadata.toSearchResponse(): SearchResponse? {
        if (type == "movie") {
            return MovieSearchResponse(
                title ?: "",
                ratingKey ?: return null,
                this@ThenosProvider.name,
                TvType.Movie,
                art?.let { "$apiUrl$it" },
                year

            )
        } else if (type == "show") {
            return TvSeriesSearchResponse(
                title ?: "",
                ratingKey ?: return null,
                this@ThenosProvider.name,
                TvType.TvSeries,
                art?.let { "$apiUrl$it" },
                year,
                null
            )
        }
        return null
    }

    private fun searchFromUrl(url: String): List<SearchResponse> {
        val response = khttp.get(url)
        val test = mapper.readValue<ThenosSearchResponse>(response.text)
        val returnValue = ArrayList<SearchResponse>()

        test.Hub?.forEach {
            it.Metadata?.forEach metadata@{
                if (it.ratingKey == null || it.title == null) return@metadata
                it.toSearchResponse()?.let { response -> returnValue.add(response) }
            }
        }

        return returnValue
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/library/search/advance?query=$query"
        return searchFromUrl(url)
    }

    data class ThenosSource(
        @JsonProperty("title") val title: String?,
        @JsonProperty("image") val image: String?,
        @JsonProperty("sources") val sources: List<Sources>?,
        @JsonProperty("tracks") val tracks: List<Tracks>
    )

    data class Sources(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("default") val default: Boolean?,
        @JsonProperty("type") val type: String?
    )

    data class Tracks(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("kind") val kind: String?
    )

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = "https://api.thenos.org/library/watch/$data"
        val response = khttp.get(url)
        val mapped = mapper.readValue<ThenosSource>(response.text)

        mapped.sources?.forEach {
            val isM3u8 = it.type != "video/mp4"
            val token = khttp.get("https://token.noss.workers.dev/").text
            val authorization =
                String(android.util.Base64.decode(token, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "${this.name} ${it.label ?: ""}",
                    (it.file)?.split("/")?.lastOrNull()?.let {
                        "https://www.googleapis.com/drive/v3/files/$it?alt=media"
                    } ?: return@forEach,
                    "https://www.thenos.org/",
                    getQualityFromName(it.label ?: ""),
                    isM3u8,
                    mapOf("authorization" to "Bearer $authorization")
                )
            )
        }

        mapped.tracks.forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.label ?: "English",
                    it.file ?: return@forEach
                )
            )
        }

        return true
    }

    data class ThenosLoadResponse(
        @JsonProperty("size") val size: Long?,
        @JsonProperty("allowSync") val allowSync: Boolean?,
        @JsonProperty("augmentationKey") val augmentationKey: String?,
        @JsonProperty("identifier") val identifier: String?,
        @JsonProperty("librarySectionID") val librarySectionID: Long?,
        @JsonProperty("librarySectionTitle") val librarySectionTitle: String?,
        @JsonProperty("librarySectionUUID") val librarySectionUUID: String?,
        @JsonProperty("mediaTagPrefix") val mediaTagPrefix: String?,
        @JsonProperty("mediaTagVersion") val mediaTagVersion: Long?,
        @JsonProperty("Metadata") val Metadata: List<Metadata?>?
    )


    data class ThenosSeriesResponse(
        @JsonProperty("size") val size: Long?,
        @JsonProperty("allowSync") val allowSync: Boolean?,
        @JsonProperty("art") val art: String?,
        @JsonProperty("identifier") val identifier: String?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("librarySectionID") val librarySectionID: Long?,
        @JsonProperty("librarySectionTitle") val librarySectionTitle: String?,
        @JsonProperty("librarySectionUUID") val librarySectionUUID: String?,
        @JsonProperty("mediaTagPrefix") val mediaTagPrefix: String?,
        @JsonProperty("mediaTagVersion") val mediaTagVersion: Long?,
        @JsonProperty("nocache") val nocache: Boolean?,
        @JsonProperty("parentIndex") val parentIndex: Long?,
        @JsonProperty("parentTitle") val parentTitle: String?,
        @JsonProperty("parentYear") val parentYear: Long?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("theme") val theme: String?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("title1") val title1: String?,
        @JsonProperty("title2") val title2: String?,
        @JsonProperty("viewGroup") val viewGroup: String?,
        @JsonProperty("viewMode") val viewMode: Long?,
        @JsonProperty("Metadata") val Metadata: List<SeriesMetadata>?
    )

    data class SeriesMetadata(
        @JsonProperty("ratingKey") val ratingKey: String?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("parentRatingKey") val parentRatingKey: String?,
        @JsonProperty("guid") val guid: String?,
        @JsonProperty("parentGuid") val parentGuid: String?,
        @JsonProperty("parentStudio") val parentStudio: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("parentKey") val parentKey: String?,
        @JsonProperty("parentTitle") val parentTitle: String?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("index") val index: Long?,
        @JsonProperty("parentIndex") val parentIndex: Long?,
        @JsonProperty("parentYear") val parentYear: Long?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("art") val art: String?,
        @JsonProperty("parentThumb") val parentThumb: String?,
        @JsonProperty("parentTheme") val parentTheme: String?,
        @JsonProperty("leafCount") val leafCount: Long?,
        @JsonProperty("viewedLeafCount") val viewedLeafCount: Long?,
        @JsonProperty("addedAt") val addedAt: Long?,
        @JsonProperty("updatedAt") val updatedAt: Int?
    )

    data class SeasonResponse(
        @JsonProperty("size") val size: Long?,
        @JsonProperty("allowSync") val allowSync: Boolean?,
        @JsonProperty("art") val art: String?,
        @JsonProperty("grandparentContentRating") val grandparentContentRating: String?,
        @JsonProperty("grandparentRatingKey") val grandparentRatingKey: Long?,
        @JsonProperty("grandparentStudio") val grandparentStudio: String?,
        @JsonProperty("grandparentTheme") val grandparentTheme: String?,
        @JsonProperty("grandparentThumb") val grandparentThumb: String?,
        @JsonProperty("grandparentTitle") val grandparentTitle: String?,
        @JsonProperty("identifier") val identifier: String?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("librarySectionID") val librarySectionID: Long?,
        @JsonProperty("librarySectionTitle") val librarySectionTitle: String?,
        @JsonProperty("librarySectionUUID") val librarySectionUUID: String?,
        @JsonProperty("mediaTagPrefix") val mediaTagPrefix: String?,
        @JsonProperty("mediaTagVersion") val mediaTagVersion: Long?,
        @JsonProperty("nocache") val nocache: Boolean?,
        @JsonProperty("parentIndex") val parentIndex: Long?,
        @JsonProperty("parentTitle") val parentTitle: String?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("theme") val theme: String?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("title1") val title1: String?,
        @JsonProperty("title2") val title2: String?,
        @JsonProperty("viewGroup") val viewGroup: String?,
        @JsonProperty("viewMode") val viewMode: Long?,
        @JsonProperty("Metadata") val Metadata: List<SeasonMetadata>?
    )

    data class SeasonMetadata(
        @JsonProperty("ratingKey") val ratingKey: String?,
        @JsonProperty("key") val key: String?,
        @JsonProperty("parentRatingKey") val parentRatingKey: String?,
        @JsonProperty("grandparentRatingKey") val grandparentRatingKey: String?,
        @JsonProperty("guid") val guid: String?,
        @JsonProperty("parentGuid") val parentGuid: String?,
        @JsonProperty("grandparentGuid") val grandparentGuid: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("grandparentKey") val grandparentKey: String?,
        @JsonProperty("parentKey") val parentKey: String?,
        @JsonProperty("grandparentTitle") val grandparentTitle: String?,
        @JsonProperty("parentTitle") val parentTitle: String?,
        @JsonProperty("contentRating") val contentRating: String?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("index") val index: Int?,
        @JsonProperty("parentIndex") val parentIndex: Int?,
        @JsonProperty("audienceRating") val audienceRating: Double?,
        @JsonProperty("thumb") val thumb: String?,
        @JsonProperty("art") val art: String?,
        @JsonProperty("parentThumb") val parentThumb: String?,
        @JsonProperty("grandparentThumb") val grandparentThumb: String?,
        @JsonProperty("grandparentArt") val grandparentArt: String?,
        @JsonProperty("grandparentTheme") val grandparentTheme: String?,
        @JsonProperty("duration") val duration: Long?,
        @JsonProperty("originallyAvailableAt") val originallyAvailableAt: String?,
        @JsonProperty("addedAt") val addedAt: Long?,
        @JsonProperty("updatedAt") val updatedAt: Long?,
        @JsonProperty("audienceRatingImage") val audienceRatingImage: String?,
        @JsonProperty("Media") val Media: List<Media>?,
        @JsonProperty("Director") val Director: List<Director>?,
        @JsonProperty("Role") val Role: List<Role>?
    )

    private fun getAllEpisodes(id: String): List<TvSeriesEpisode> {
        val episodes = ArrayList<TvSeriesEpisode>()
        val url = "$apiUrl/library/metadata/$id/children"
        val response = khttp.get(url)
        val mapped = mapper.readValue<ThenosSeriesResponse>(response.text)
        mapped.Metadata?.forEach {
            val fixedUrl = "https://api.thenos.org" + it.key
            val child = khttp.get(fixedUrl)
            val mappedSeason = mapper.readValue<SeasonResponse>(child.text)
            mappedSeason.Metadata?.forEach mappedSeason@{
                episodes.add(
                    TvSeriesEpisode(
                        it.title,
                        it.parentIndex,
                        it.index,
                        it.ratingKey ?: return@mappedSeason,
                        it.thumb?.let { "$apiUrl$it" },
                        it.originallyAvailableAt,
                        (it.audienceRating?.times(10))?.toInt(),
                        it.summary
                    )
                )
            }

        }
        return episodes
    }

    override fun load(url: String): LoadResponse? {
        val fixedUrl = "$apiUrl/library/metadata/${url.split("/").last()}"
        val response = khttp.get(fixedUrl)
        val mapped = mapper.readValue<ThenosLoadResponse>(response.text)

        val isShow = mapped.Metadata?.any { it?.type == "show" } == true
        val metadata = mapped.Metadata?.getOrNull(0) ?: return null

        return if (!isShow) {
            MovieLoadResponse(
                metadata.title ?: "No title found",
                "$mainUrl/movie/${metadata.ratingKey}",
                this.name,
                TvType.Movie,
                metadata.ratingKey ?: return null,
                metadata.art?.let { "$apiUrl$it" },
                metadata.year,
                metadata.summary,
                null, // with Guid this is possible
                metadata.audienceRating?.times(10),
                metadata.Genre?.mapNotNull { it.tag },
                metadata.duration?.let { secondsToReadable(it / 1000, "") },
                null
            )
        } else {
            TvSeriesLoadResponse(
                metadata.title ?: "No title found",
                "$mainUrl/show/${metadata.ratingKey}",
                this.name,
                TvType.TvSeries,
                metadata.ratingKey?.let { getAllEpisodes(it) } ?: return null,
                metadata.art?.let { "$apiUrl$it" },
                metadata.year,
                metadata.summary,
                null, // with Guid this is possible
                null,// with Guid this is possible
                metadata.audienceRating?.times(10),
                metadata.Genre?.mapNotNull { it.tag },
                metadata.duration?.let { secondsToReadable(it / 1000, "") },
                null
            )
        }
    }
}