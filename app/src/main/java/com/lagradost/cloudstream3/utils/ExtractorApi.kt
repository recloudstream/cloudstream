package com.lagradost.cloudstream3.utils

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnore
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.AStreamHub
import com.lagradost.cloudstream3.extractors.Acefile
import com.lagradost.cloudstream3.extractors.Ahvsh
import com.lagradost.cloudstream3.extractors.Aico
import com.lagradost.cloudstream3.extractors.AsianLoad
import com.lagradost.cloudstream3.extractors.Bestx
import com.lagradost.cloudstream3.extractors.Blogger
import com.lagradost.cloudstream3.extractors.BullStream
import com.lagradost.cloudstream3.extractors.ByteShare
import com.lagradost.cloudstream3.extractors.Cda
import com.lagradost.cloudstream3.extractors.Cdnplayer
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.CineGrabber
import com.lagradost.cloudstream3.extractors.Cinestart
import com.lagradost.cloudstream3.extractors.DBfilm
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.DatabaseGdrive
import com.lagradost.cloudstream3.extractors.DatabaseGdrive2
import com.lagradost.cloudstream3.extractors.DesuArcg
import com.lagradost.cloudstream3.extractors.DesuDrive
import com.lagradost.cloudstream3.extractors.DesuOdchan
import com.lagradost.cloudstream3.extractors.DesuOdvip
import com.lagradost.cloudstream3.extractors.Dokicloud
import com.lagradost.cloudstream3.extractors.DoodCxExtractor
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.DoodPmExtractor
import com.lagradost.cloudstream3.extractors.DoodShExtractor
import com.lagradost.cloudstream3.extractors.DoodSoExtractor
import com.lagradost.cloudstream3.extractors.DoodToExtractor
import com.lagradost.cloudstream3.extractors.DoodWatchExtractor
import com.lagradost.cloudstream3.extractors.DoodWfExtractor
import com.lagradost.cloudstream3.extractors.DoodWsExtractor
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.Dooood
import com.lagradost.cloudstream3.extractors.Embedgram
import com.lagradost.cloudstream3.extractors.Evoload
import com.lagradost.cloudstream3.extractors.Evoload1
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.FEnet
import com.lagradost.cloudstream3.extractors.Fastream
import com.lagradost.cloudstream3.extractors.FeHD
import com.lagradost.cloudstream3.extractors.Fembed9hd
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FileMoonIn
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.Fplayer
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.Gdriveplayer
import com.lagradost.cloudstream3.extractors.Gdriveplayerapi
import com.lagradost.cloudstream3.extractors.Gdriveplayerapp
import com.lagradost.cloudstream3.extractors.Gdriveplayerbiz
import com.lagradost.cloudstream3.extractors.Gdriveplayerco
import com.lagradost.cloudstream3.extractors.Gdriveplayerfun
import com.lagradost.cloudstream3.extractors.Gdriveplayerio
import com.lagradost.cloudstream3.extractors.Gdriveplayerme
import com.lagradost.cloudstream3.extractors.Gdriveplayerorg
import com.lagradost.cloudstream3.extractors.Gdriveplayerus
import com.lagradost.cloudstream3.extractors.Gofile
import com.lagradost.cloudstream3.extractors.GuardareStream
import com.lagradost.cloudstream3.extractors.Guccihide
import com.lagradost.cloudstream3.extractors.Hxfile
import com.lagradost.cloudstream3.extractors.JWPlayer
import com.lagradost.cloudstream3.extractors.Jawcloud
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.Keephealth
import com.lagradost.cloudstream3.extractors.KotakAnimeid
import com.lagradost.cloudstream3.extractors.Kotakajair
import com.lagradost.cloudstream3.extractors.Krakenfiles
import com.lagradost.cloudstream3.extractors.LayarKaca
import com.lagradost.cloudstream3.extractors.Linkbox
import com.lagradost.cloudstream3.extractors.Luxubu
import com.lagradost.cloudstream3.extractors.Lvturbo
import com.lagradost.cloudstream3.extractors.Maxstream
import com.lagradost.cloudstream3.extractors.Mcloud
import com.lagradost.cloudstream3.extractors.Megacloud
import com.lagradost.cloudstream3.extractors.Meownime
import com.lagradost.cloudstream3.extractors.Minoplres
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.MixDropBz
import com.lagradost.cloudstream3.extractors.MixDropCh
import com.lagradost.cloudstream3.extractors.MixDropTo
import com.lagradost.cloudstream3.extractors.Movhide
import com.lagradost.cloudstream3.extractors.Moviehab
import com.lagradost.cloudstream3.extractors.MoviehabNet
import com.lagradost.cloudstream3.extractors.Moviesapi
import com.lagradost.cloudstream3.extractors.Moviesm4u
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.Mvidoo
import com.lagradost.cloudstream3.extractors.MwvnVizcloudInfo
import com.lagradost.cloudstream3.extractors.Neonime7n
import com.lagradost.cloudstream3.extractors.Neonime8n
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.TauVideo
import com.lagradost.cloudstream3.extractors.SibNet
import com.lagradost.cloudstream3.extractors.ContentX
import com.lagradost.cloudstream3.extractors.Hotlinger
import com.lagradost.cloudstream3.extractors.HDMomPlayer
import com.lagradost.cloudstream3.extractors.HDPlayerSystem
import com.lagradost.cloudstream3.extractors.VideoSeyred
import com.lagradost.cloudstream3.extractors.PeaceMakerst
import com.lagradost.cloudstream3.extractors.HDStreamAble
import com.lagradost.cloudstream3.extractors.RapidVid
import com.lagradost.cloudstream3.extractors.TRsTX
import com.lagradost.cloudstream3.extractors.VidMoxy
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.extractors.MailRu
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.OkRuHTTP
import com.lagradost.cloudstream3.extractors.Okrulink
import com.lagradost.cloudstream3.extractors.PlayLtXyz
import com.lagradost.cloudstream3.extractors.PlayerVoxzer
import com.lagradost.cloudstream3.extractors.Rabbitstream
import com.lagradost.cloudstream3.extractors.Rasacintaku
import com.lagradost.cloudstream3.extractors.SBfull
import com.lagradost.cloudstream3.extractors.Sbasian
import com.lagradost.cloudstream3.extractors.Sbface
import com.lagradost.cloudstream3.extractors.Sbflix
import com.lagradost.cloudstream3.extractors.Sblona
import com.lagradost.cloudstream3.extractors.Sblongvu
import com.lagradost.cloudstream3.extractors.Sbnet
import com.lagradost.cloudstream3.extractors.Sbrapid
import com.lagradost.cloudstream3.extractors.Sbsonic
import com.lagradost.cloudstream3.extractors.Sbspeed
import com.lagradost.cloudstream3.extractors.Sbthe
import com.lagradost.cloudstream3.extractors.Sendvid
import com.lagradost.cloudstream3.extractors.ShaveTape
import com.lagradost.cloudstream3.extractors.Solidfiles
import com.lagradost.cloudstream3.extractors.Ssbstream
import com.lagradost.cloudstream3.extractors.StreamM4u
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamSB1
import com.lagradost.cloudstream3.extractors.StreamSB10
import com.lagradost.cloudstream3.extractors.StreamSB11
import com.lagradost.cloudstream3.extractors.StreamSB2
import com.lagradost.cloudstream3.extractors.StreamSB3
import com.lagradost.cloudstream3.extractors.StreamSB4
import com.lagradost.cloudstream3.extractors.StreamSB5
import com.lagradost.cloudstream3.extractors.StreamSB6
import com.lagradost.cloudstream3.extractors.StreamSB7
import com.lagradost.cloudstream3.extractors.StreamSB8
import com.lagradost.cloudstream3.extractors.StreamSB9
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamTapeNet
import com.lagradost.cloudstream3.extractors.StreamhideCom
import com.lagradost.cloudstream3.extractors.StreamhideTo
import com.lagradost.cloudstream3.extractors.Streamhub2
import com.lagradost.cloudstream3.extractors.Streamlare
import com.lagradost.cloudstream3.extractors.StreamoUpload
import com.lagradost.cloudstream3.extractors.Streamplay
import com.lagradost.cloudstream3.extractors.Streamsss
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.Tantifilm
import com.lagradost.cloudstream3.extractors.Tomatomatela
import com.lagradost.cloudstream3.extractors.TomatomatelalClub
import com.lagradost.cloudstream3.extractors.Tubeless
import com.lagradost.cloudstream3.extractors.Upstream
import com.lagradost.cloudstream3.extractors.UpstreamExtractor
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.extractors.Uqload1
import com.lagradost.cloudstream3.extractors.Uqload2
import com.lagradost.cloudstream3.extractors.Userload
import com.lagradost.cloudstream3.extractors.Userscloud
import com.lagradost.cloudstream3.extractors.Uservideo
import com.lagradost.cloudstream3.extractors.Vanfem
import com.lagradost.cloudstream3.extractors.Vicloud
import com.lagradost.cloudstream3.extractors.VidSrcExtractor
import com.lagradost.cloudstream3.extractors.VidSrcExtractor2
import com.lagradost.cloudstream3.extractors.VideoVard
import com.lagradost.cloudstream3.extractors.VideovardSX
import com.lagradost.cloudstream3.extractors.Vidgomunime
import com.lagradost.cloudstream3.extractors.Vidgomunimesb
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.extractors.Vido
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.extractors.Vidstreamz
import com.lagradost.cloudstream3.extractors.Vizcloud
import com.lagradost.cloudstream3.extractors.Vizcloud2
import com.lagradost.cloudstream3.extractors.VizcloudCloud
import com.lagradost.cloudstream3.extractors.VizcloudDigital
import com.lagradost.cloudstream3.extractors.VizcloudInfo
import com.lagradost.cloudstream3.extractors.VizcloudLive
import com.lagradost.cloudstream3.extractors.VizcloudOnline
import com.lagradost.cloudstream3.extractors.VizcloudSite
import com.lagradost.cloudstream3.extractors.VizcloudXyz
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.Watchx
import com.lagradost.cloudstream3.extractors.WcoStream
import com.lagradost.cloudstream3.extractors.Wibufile
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.YourUpload
import com.lagradost.cloudstream3.extractors.YoutubeExtractor
import com.lagradost.cloudstream3.extractors.YoutubeMobileExtractor
import com.lagradost.cloudstream3.extractors.YoutubeNoCookieExtractor
import com.lagradost.cloudstream3.extractors.YoutubeShortLinkExtractor
import com.lagradost.cloudstream3.extractors.Yufiles
import com.lagradost.cloudstream3.extractors.Zorofile
import com.lagradost.cloudstream3.extractors.Zplayer
import com.lagradost.cloudstream3.extractors.ZplayerV2
import com.lagradost.cloudstream3.extractors.Ztreamhub
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import java.net.URL
import java.util.UUID

/**
 * For use in the ConcatenatingMediaSource.
 * If features are missing (headers), please report and we can add it.
 * @param durationUs use Long.toUs() for easier input
 * */
data class PlayListItem(
    val url: String,
    val durationUs: Long,
)

/**
 * Converts Seconds to MicroSeconds, multiplication by 1_000_000
 * */
fun Long.toUs(): Long {
    return this * 1_000_000
}

/**
 * If your site has an unorthodox m3u8-like system where there are multiple smaller videos concatenated
 * use this.
 * */
data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override val referer: String,
    override val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override val extractorData: String? = null,
    override val type: ExtractorLinkType,
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    headers = headers,
    extractorData = extractorData,
    type = type
) {
    constructor(
        source: String,
        name: String,
        playlist: List<PlayListItem>,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        playlist = playlist,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
    )
}

/** Metadata about the file type used for downloads and exoplayer hint,
 * if you respond with the wrong one the file will fail to download or be played */
enum class ExtractorLinkType {
    /** Single stream of bytes no matter the actual file type */
    VIDEO,
    /** Split into several .ts files, has support for encrypted m3u8s */
    M3U8,
    /** Like m3u8 but uses xml, currently no download support */
    DASH,
    /** No support at the moment */
    TORRENT,
    /** No support at the moment */
    MAGNET,
}

private fun inferTypeFromUrl(url: String): ExtractorLinkType {
    val path = normalSafeApiCall { URL(url).path }
    return when {
        path?.endsWith(".m3u8") == true -> ExtractorLinkType.M3U8
        path?.endsWith(".mpd") == true -> ExtractorLinkType.DASH
        path?.endsWith(".torrent") == true -> ExtractorLinkType.TORRENT
        url.startsWith("magnet:") -> ExtractorLinkType.MAGNET
        else -> ExtractorLinkType.VIDEO
    }
}
val INFER_TYPE : ExtractorLinkType? = null

/**
 * UUID for the ClearKey DRM scheme.
 *
 *
 * ClearKey is supported on Android devices running Android 5.0 (API Level 21) and up.
 */
val CLEARKEY_UUID = UUID(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

/**
 * UUID for the Widevine DRM scheme.
 *
 *
 * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
 */
val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

/**
 * UUID for the PlayReady DRM scheme.
 *
 *
 * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
 * provide PlayReady support.
 */
val PLAYREADY_UUID = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

open class DrmExtractorLink private constructor(
    override val source: String,
    override val name: String,
    override val url: String,
    override val referer: String,
    override val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override val extractorData: String? = null,
    override val type: ExtractorLinkType,
    open val kid : String,
    open val key : String,
    open val uuid : UUID,
    open val kty : String,

    open val keyRequestParameters : HashMap<String, String>
) : ExtractorLink(
    source, name, url, referer, quality, type, headers, extractorData
) {
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        kid : String,
        key : String,
        uuid : UUID = CLEARKEY_UUID,
        kty : String = "oct",
        keyRequestParameters : HashMap<String, String> = hashMapOf(),
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        keyRequestParameters = keyRequestParameters,
        kty = kty,
    )
}

open class ExtractorLink constructor(
    open val source: String,
    open val name: String,
    override val url: String,
    override val referer: String,
    open val quality: Int,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    open val extractorData: String? = null,
    open val type: ExtractorLinkType,
) : VideoDownloadManager.IDownloadableMinimum {
    val isM3u8 : Boolean get() = type == ExtractorLinkType.M3U8
    val isDash : Boolean get() = type == ExtractorLinkType.DASH
    
    @JsonIgnore
    fun getAllHeaders() : Map<String, String> {
        if (referer.isBlank()) {
            return headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            return headers + mapOf("referer" to referer)
        }
        return headers
    }

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url)
    )

    /**
     * Old constructor without isDash, allows for backwards compatibility with extensions.
     * Should be removed after all extensions have updated their cloudstream.jar
     **/
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null
    ) : this(source, name, url, referer, quality, isM3u8, headers, extractorData, false)

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        isDash: Boolean,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = if (isDash) ExtractorLinkType.DASH else if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
    )

    override fun toString(): String {
        return "ExtractorLink(name=$name, url=$url, referer=$referer, type=$type)"
    }
}

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null,
    val tvType: TvType? = null,
)

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    override val referer: String,
    override val headers: Map<String, String> = mapOf()
) : VideoDownloadManager.IDownloadableMinimum

/**
 * Removes https:// and www.
 * To match urls regardless of schema, perhaps Uri() can be used?
 */
val schemaStripRegex = Regex("""^(https:|)//(www\.|)""")

enum class Qualities(var value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0), // 144p
    P240(240, 2), // 240p
    P360(360, 3), // 360p
    P480(480, 4), // 480p
    P720(720, 5), // 720p
    P1080(1080, 6), // 1080p
    P1440(1440, 7), // 1440p
    P2160(2160, 8); // 4k or 2160p

    companion object {
        fun getStringByInt(qual: Int?): String {
            return when (qual) {
                0 -> "Auto"
                Unknown.value -> ""
                P2160.value -> "4K"
                null -> ""
                else -> "${qual}p"
            }
        }

        fun getStringByIntFull(quality: Int): String {
            return when (quality) {
                0 -> "Auto"
                Unknown.value -> "Unknown"
                P2160.value -> "4K"
                else -> "${quality}p"
            }
        }
    }
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null)
        return Qualities.Unknown.value

    val match = qualityName.lowercase().replace("p", "").trim()
    return when (match) {
        "4k" -> Qualities.P2160
        else -> null
    }?.value ?: match.toIntOrNull() ?: Qualities.Unknown.value
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack() ?: string
}

suspend fun unshortenLinkSafe(url: String): String {
    return try {
        if (ShortLink.isShortLink(url))
            ShortLink.unshorten(url)
        else url
    } catch (e: Exception) {
        logError(e)
        url
    }
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return loadExtractor(
        url = url,
        referer = null,
        subtitleCallback = subtitleCallback,
        callback = callback
    )
}

/**
 * Tries to load the appropriate extractor based on link, returns true if any extractor is loaded.
 * */
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val currentUrl = unshortenLinkSafe(url)
    val compareUrl = currentUrl.lowercase().replace(schemaStripRegex, "")
    for (extractor in extractorApis) {
        if (compareUrl.startsWith(extractor.mainUrl.replace(schemaStripRegex, ""))) {
            extractor.getSafeUrl(currentUrl, referer, subtitleCallback, callback)
            return true
        }
    }

    return false
}

val extractorApis: MutableList<ExtractorApi> = arrayListOf(
    //AllProvider(),
    WcoStream(),
    Vidstreamz(),
    Vizcloud(),
    Vizcloud2(),
    VizcloudOnline(),
    VizcloudXyz(),
    VizcloudLive(),
    VizcloudInfo(),
    MwvnVizcloudInfo(),
    VizcloudDigital(),
    VizcloudCloud(),
    VizcloudSite(),
    VideoVard(),
    VideovardSX(),
    Mp4Upload(),
    StreamTape(),
    StreamTapeNet(),
    ShaveTape(),

    //mixdrop extractors
    MixDropBz(),
    MixDropCh(),
    MixDropTo(),

    MixDrop(),

    Mcloud(),
    XStreamCdn(),

    StreamSB(),
    Sblona(),
    Vidgomunimesb(),
    StreamSB1(),
    StreamSB2(),
    StreamSB3(),
    StreamSB4(),
    StreamSB5(),
    StreamSB6(),
    StreamSB7(),
    StreamSB8(),
    StreamSB9(),
    StreamSB10(),
    StreamSB11(),
    SBfull(),
    // Streamhub(), cause Streamhub2() works
    Streamhub2(),
    Ssbstream(),
    Sbthe(),
    Vidgomunime(),
    Sbflix(),
    Streamsss(),
    Sbspeed(),
    Sbsonic(),
    Sbface(),
    Sbrapid(),
    Lvturbo(),

    Fastream(),

    FEmbed(),
    FeHD(),
    Fplayer(),
    DBfilm(),
    Luxubu(),
    LayarKaca(),
    Rasacintaku(),
    FEnet(),
    Kotakajair(),
    Cdnplayer(),
    //  WatchSB(), 'cause StreamSB.kt works
    Uqload(),
    Uqload1(),
    Uqload2(),
    Evoload(),
    Evoload1(),
    UpstreamExtractor(),

    Odnoklassniki(),
    TauVideo(),
    SibNet(),
    ContentX(),
    Hotlinger(),
    HDMomPlayer(),
    HDPlayerSystem(),
    VideoSeyred(),
    PeaceMakerst(),
    HDStreamAble(),
    RapidVid(),
    TRsTX(),
    VidMoxy(),
    PixelDrain(),
    MailRu(),

    Tomatomatela(),
    TomatomatelalClub(),
    Cinestart(),
    OkRuSSL(),
    OkRuHTTP(),
    Okrulink(),
    Sendvid(),

    // dood extractors
    DoodCxExtractor(),
    DoodPmExtractor(),
    DoodToExtractor(),
    DoodSoExtractor(),
    DoodLaExtractor(),
    Dooood(),
    DoodWsExtractor(),
    DoodShExtractor(),
    DoodWatchExtractor(),
    DoodWfExtractor(),
    DoodYtExtractor(),

    AsianLoad(),

    // GenericM3U8(),
    Jawcloud(),
    Zplayer(),
    ZplayerV2(),
    Upstream(),

    Maxstream(),
    Tantifilm(),
    Userload(),
    Supervideo(),
    GuardareStream(),
    CineGrabber(),
    Vanfem(),

    // StreamSB.kt works
    //  SBPlay(),
    //  SBPlay1(),
    //  SBPlay2(),

    PlayerVoxzer(),

    BullStream(),
    GMPlayer(),

    Blogger(),
    Solidfiles(),
    YourUpload(),

    Hxfile(),
    KotakAnimeid(),
    Neonime8n(),
    Neonime7n(),
    Yufiles(),
    Aico(),

    JWPlayer(),
    Meownime(),
    DesuArcg(),
    DesuOdchan(),
    DesuOdvip(),
    DesuDrive(),

    Chillx(),
    Moviesapi(),
    Watchx(),
    Bestx(),
    Keephealth(),
    Sbnet(),
    Sbasian(),
    Sblongvu(),
    Fembed9hd(),
    StreamM4u(),
    Krakenfiles(),
    Gofile(),
    Vicloud(),
    Uservideo(),
    Userscloud(),

    Movhide(),
    StreamhideCom(),
    StreamhideTo(),
    Wibufile(),
    FileMoonIn(),
    Moviesm4u(),
    Filesim(),
    Ahvsh(),
    Guccihide(),
    FileMoon(),
    FileMoonSx(),
    Vido(),
    Linkbox(),
    Acefile(),
    Minoplres(), // formerly SpeedoStream
    Zorofile(),
    Embedgram(),
    Mvidoo(),
    Streamplay(),
    Vidmoly(),
    Vidmolyme(),
    Voe(),
    Tubeless(),
    Moviehab(),
    MoviehabNet(),
    Jeniusplay(),
    StreamoUpload(),

    Gdriveplayerapi(),
    Gdriveplayerapp(),
    Gdriveplayerfun(),
    Gdriveplayerio(),
    Gdriveplayerme(),
    Gdriveplayerbiz(),
    Gdriveplayerorg(),
    Gdriveplayerus(),
    Gdriveplayerco(),
    Gdriveplayer(),
    DatabaseGdrive(),
    DatabaseGdrive2(),

    YoutubeExtractor(),
    YoutubeShortLinkExtractor(),
    YoutubeMobileExtractor(),
    YoutubeNoCookieExtractor(),
    Streamlare(),
    VidSrcExtractor(),
    VidSrcExtractor2(),
    PlayLtXyz(),
    AStreamHub(),
    Vidplay(),

    Cda(),
    Dailymotion(),
    ByteShare(),
    Ztreamhub(),
    Rabbitstream(),
    Dokicloud(),
    Megacloud(),
)


fun getExtractorApiFromName(name: String): ExtractorApi {
    for (api in extractorApis) {
        if (api.name == name) return api
    }
    return extractorApis[0]
}

fun requireReferer(name: String): Boolean {
    return getExtractorApiFromName(name).requiresReferer
}

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

suspend fun getPostForm(requestUrl: String, html: String): String? {
    val document = Jsoup.parse(html)
    val inputs = document.select("Form > input")
    if (inputs.size < 4) return null
    var op: String? = null
    var id: String? = null
    var mode: String? = null
    var hash: String? = null

    for (input in inputs) {
        val value = input.attr("value") ?: continue
        when (input.attr("name")) {
            "op" -> op = value
            "id" -> id = value
            "mode" -> mode = value
            "hash" -> hash = value
            else -> Unit
        }
    }
    if (op == null || id == null || mode == null || hash == null) {
        return null
    }
    delay(5000) // ye this is needed, wont work with 0 delay

    return app.post(
        requestUrl,
        headers = mapOf(
            "content-type" to "application/x-www-form-urlencoded",
            "referer" to requestUrl,
            "user-agent" to USER_AGENT,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        ),
        data = mapOf("op" to op, "id" to id, "mode" to mode, "hash" to hash)
    ).text
}

fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http") ||
        // Do not fix JSON objects when passed as urls.
        url.startsWith("{\"")
    ) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given extractor is from */
    var sourcePlugin: String? = null

    //suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
    //    return suspendSafeApiCall { getUrl(url, referer) }
    //}

    // this is the new extractorapi, override to add subtitles and stuff
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
