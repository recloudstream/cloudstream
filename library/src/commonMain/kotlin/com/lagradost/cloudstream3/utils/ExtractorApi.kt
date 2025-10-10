package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import com.lagradost.cloudstream3.IDownloadableMinimum
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.AStreamHub
import com.lagradost.cloudstream3.extractors.Acefile
import com.lagradost.cloudstream3.extractors.Ahvsh
import com.lagradost.cloudstream3.extractors.Aico
import com.lagradost.cloudstream3.extractors.AnimesagaStream
import com.lagradost.cloudstream3.extractors.Anplay
import com.lagradost.cloudstream3.extractors.AsianLoad
import com.lagradost.cloudstream3.extractors.Asnwish
import com.lagradost.cloudstream3.extractors.Awish
import com.lagradost.cloudstream3.extractors.Beastx
import com.lagradost.cloudstream3.extractors.Bestx
import com.lagradost.cloudstream3.extractors.BgwpCC
import com.lagradost.cloudstream3.extractors.BigwarpArt
import com.lagradost.cloudstream3.extractors.BigwarpIO
import com.lagradost.cloudstream3.extractors.Blogger
import com.lagradost.cloudstream3.extractors.Boltx
import com.lagradost.cloudstream3.extractors.Boosterx
import com.lagradost.cloudstream3.extractors.BullStream
import com.lagradost.cloudstream3.extractors.ByteShare
import com.lagradost.cloudstream3.extractors.Cda
import com.lagradost.cloudstream3.extractors.Cdnplayer
import com.lagradost.cloudstream3.extractors.CdnwishCom
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.CineGrabber
import com.lagradost.cloudstream3.extractors.Cinestart
import com.lagradost.cloudstream3.extractors.CloudMailRu
import com.lagradost.cloudstream3.extractors.ContentX
import com.lagradost.cloudstream3.extractors.CsstOnline
import com.lagradost.cloudstream3.extractors.D0000d
import com.lagradost.cloudstream3.extractors.D000dCom
import com.lagradost.cloudstream3.extractors.DBfilm
import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.DatabaseGdrive
import com.lagradost.cloudstream3.extractors.DatabaseGdrive2
import com.lagradost.cloudstream3.extractors.DesuArcg
import com.lagradost.cloudstream3.extractors.DesuDrive
import com.lagradost.cloudstream3.extractors.DesuOdchan
import com.lagradost.cloudstream3.extractors.DesuOdvip
import com.lagradost.cloudstream3.extractors.Dhcplay
import com.lagradost.cloudstream3.extractors.Dhtpre
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
import com.lagradost.cloudstream3.extractors.Doodporn
import com.lagradost.cloudstream3.extractors.DoodstreamCom
import com.lagradost.cloudstream3.extractors.Dooood
import com.lagradost.cloudstream3.extractors.Ds2play
import com.lagradost.cloudstream3.extractors.Ds2video
import com.lagradost.cloudstream3.extractors.DsstOnline
import com.lagradost.cloudstream3.extractors.Dwish
import com.lagradost.cloudstream3.extractors.EPlayExtractor
import com.lagradost.cloudstream3.extractors.Embedgram
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.Evoload
import com.lagradost.cloudstream3.extractors.Evoload1
import com.lagradost.cloudstream3.extractors.Ewish
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.FEnet
import com.lagradost.cloudstream3.extractors.Fastream
import com.lagradost.cloudstream3.extractors.FeHD
import com.lagradost.cloudstream3.extractors.Fembed9hd
import com.lagradost.cloudstream3.extractors.FileMoonIn
import com.lagradost.cloudstream3.extractors.Filegram
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.FlaswishCom
import com.lagradost.cloudstream3.extractors.FourCX
import com.lagradost.cloudstream3.extractors.FourPichive
import com.lagradost.cloudstream3.extractors.FourPlayRu
import com.lagradost.cloudstream3.extractors.Fplayer
import com.lagradost.cloudstream3.extractors.FsstOnline
import com.lagradost.cloudstream3.extractors.GDMirrorbot
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.GamoVideo
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
import com.lagradost.cloudstream3.extractors.Geodailymotion
import com.lagradost.cloudstream3.extractors.Gofile
import com.lagradost.cloudstream3.extractors.GoodstreamExtractor
import com.lagradost.cloudstream3.extractors.GuardareStream
import com.lagradost.cloudstream3.extractors.Guccihide
import com.lagradost.cloudstream3.extractors.HDMomPlayer
import com.lagradost.cloudstream3.extractors.HDPlayerSystem
import com.lagradost.cloudstream3.extractors.HDStreamAble
import com.lagradost.cloudstream3.extractors.Hotlinger
import com.lagradost.cloudstream3.extractors.Hxfile
import com.lagradost.cloudstream3.extractors.InternetArchive
import com.lagradost.cloudstream3.extractors.JWPlayer
import com.lagradost.cloudstream3.extractors.Jawcloud
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.Jodwish
import com.lagradost.cloudstream3.extractors.Keephealth
import com.lagradost.cloudstream3.extractors.Kinogeru
import com.lagradost.cloudstream3.extractors.KotakAnimeid
import com.lagradost.cloudstream3.extractors.Kotakajair
import com.lagradost.cloudstream3.extractors.Krakenfiles
import com.lagradost.cloudstream3.extractors.Kswplayer
import com.lagradost.cloudstream3.extractors.LayarKaca
import com.lagradost.cloudstream3.extractors.Linkbox
import com.lagradost.cloudstream3.extractors.LuluStream
import com.lagradost.cloudstream3.extractors.Lulustream1
import com.lagradost.cloudstream3.extractors.Lulustream2
import com.lagradost.cloudstream3.extractors.Luxubu
import com.lagradost.cloudstream3.extractors.Lvturbo
import com.lagradost.cloudstream3.extractors.MailRu
import com.lagradost.cloudstream3.extractors.Maxstream
import com.lagradost.cloudstream3.extractors.Mcloud
import com.lagradost.cloudstream3.extractors.Mediafire
import com.lagradost.cloudstream3.extractors.MegaF
import com.lagradost.cloudstream3.extractors.Megacloud
import com.lagradost.cloudstream3.extractors.Meownime
import com.lagradost.cloudstream3.extractors.MetaGnathTuggers
import com.lagradost.cloudstream3.extractors.Minoplres
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.MixDropAg
import com.lagradost.cloudstream3.extractors.MixDropBz
import com.lagradost.cloudstream3.extractors.MixDropCh
import com.lagradost.cloudstream3.extractors.MixDropTo
import com.lagradost.cloudstream3.extractors.Movhide
import com.lagradost.cloudstream3.extractors.Moviehab
import com.lagradost.cloudstream3.extractors.MoviehabNet
import com.lagradost.cloudstream3.extractors.Moviesapi
import com.lagradost.cloudstream3.extractors.Moviesm4u
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.Multimovies
import com.lagradost.cloudstream3.extractors.Mvidoo
import com.lagradost.cloudstream3.extractors.Mwish
import com.lagradost.cloudstream3.extractors.MwvnVizcloudInfo
import com.lagradost.cloudstream3.extractors.MyCloud
import com.lagradost.cloudstream3.extractors.NathanFromSubject
import com.lagradost.cloudstream3.extractors.Nekostream
import com.lagradost.cloudstream3.extractors.Nekowish
import com.lagradost.cloudstream3.extractors.Neonime7n
import com.lagradost.cloudstream3.extractors.Neonime8n
import com.lagradost.cloudstream3.extractors.Obeywish
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.OkRuHTTP
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.Okrulink
import com.lagradost.cloudstream3.extractors.PeaceMakerst
import com.lagradost.cloudstream3.extractors.Peytonepre
import com.lagradost.cloudstream3.extractors.Pichive
import com.lagradost.cloudstream3.extractors.PixelDrain
import com.lagradost.cloudstream3.extractors.PlayLtXyz
import com.lagradost.cloudstream3.extractors.PlayRu
import com.lagradost.cloudstream3.extractors.PlayerVoxzer
import com.lagradost.cloudstream3.extractors.Playerwish
import com.lagradost.cloudstream3.extractors.Playerx
import com.lagradost.cloudstream3.extractors.Rabbitstream
import com.lagradost.cloudstream3.extractors.RapidVid
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
import com.lagradost.cloudstream3.extractors.SecvideoOnline
import com.lagradost.cloudstream3.extractors.Sendvid
import com.lagradost.cloudstream3.extractors.Server1uns
import com.lagradost.cloudstream3.extractors.SfastwishCom
import com.lagradost.cloudstream3.extractors.ShaveTape
import com.lagradost.cloudstream3.extractors.SibNet
import com.lagradost.cloudstream3.extractors.Simpulumlamerop
import com.lagradost.cloudstream3.extractors.Smoothpre
import com.lagradost.cloudstream3.extractors.Sobreatsesuyp
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
import com.lagradost.cloudstream3.extractors.StreamSilk
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamTapeNet
import com.lagradost.cloudstream3.extractors.StreamTapeXyz
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.StreamhideCom
import com.lagradost.cloudstream3.extractors.StreamhideTo
import com.lagradost.cloudstream3.extractors.Streamhub2
import com.lagradost.cloudstream3.extractors.Streamlare
import com.lagradost.cloudstream3.extractors.StreamoUpload
import com.lagradost.cloudstream3.extractors.Streamplay
import com.lagradost.cloudstream3.extractors.Streamsss
import com.lagradost.cloudstream3.extractors.Streamup
import com.lagradost.cloudstream3.extractors.Streamwish2
import com.lagradost.cloudstream3.extractors.Strwish
import com.lagradost.cloudstream3.extractors.Strwish2
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.Swdyu
import com.lagradost.cloudstream3.extractors.Swhoi
import com.lagradost.cloudstream3.extractors.TRsTX
import com.lagradost.cloudstream3.extractors.Tantifilm
import com.lagradost.cloudstream3.extractors.TauVideo
import com.lagradost.cloudstream3.extractors.Tomatomatela
import com.lagradost.cloudstream3.extractors.TomatomatelalClub
import com.lagradost.cloudstream3.extractors.Tubeless
import com.lagradost.cloudstream3.extractors.Upstream
import com.lagradost.cloudstream3.extractors.UpstreamExtractor
import com.lagradost.cloudstream3.extractors.Uqload
import com.lagradost.cloudstream3.extractors.Uqload1
import com.lagradost.cloudstream3.extractors.Uqload2
import com.lagradost.cloudstream3.extractors.UqloadsXyz
import com.lagradost.cloudstream3.extractors.Urochsunloath
import com.lagradost.cloudstream3.extractors.Userload
import com.lagradost.cloudstream3.extractors.Userscloud
import com.lagradost.cloudstream3.extractors.Uservideo
import com.lagradost.cloudstream3.extractors.Vanfem
import com.lagradost.cloudstream3.extractors.Vectorx
import com.lagradost.cloudstream3.extractors.Vicloud
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidHidePro1
import com.lagradost.cloudstream3.extractors.VidHidePro2
import com.lagradost.cloudstream3.extractors.VidHidePro3
import com.lagradost.cloudstream3.extractors.VidHidePro4
import com.lagradost.cloudstream3.extractors.VidHidePro5
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.VidMoxy
import com.lagradost.cloudstream3.extractors.VidSrcExtractor
import com.lagradost.cloudstream3.extractors.VidSrcExtractor2
import com.lagradost.cloudstream3.extractors.VidSrcTo
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VideoSeyred
import com.lagradost.cloudstream3.extractors.VideoVard
import com.lagradost.cloudstream3.extractors.VideovardSX
import com.lagradost.cloudstream3.extractors.Vidgomunime
import com.lagradost.cloudstream3.extractors.Vidgomunimesb
import com.lagradost.cloudstream3.extractors.Vidguardto
import com.lagradost.cloudstream3.extractors.Vidguardto1
import com.lagradost.cloudstream3.extractors.Vidguardto2
import com.lagradost.cloudstream3.extractors.Vidguardto3
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Vidmoly
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.extractors.Vido
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.extractors.VidplayOnline
import com.lagradost.cloudstream3.extractors.Vidstreamz
import com.lagradost.cloudstream3.extractors.Vidxstream
import com.lagradost.cloudstream3.extractors.VinovoSi
import com.lagradost.cloudstream3.extractors.VinovoTo
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
import com.lagradost.cloudstream3.extractors.Voe1
import com.lagradost.cloudstream3.extractors.Vtbe
import com.lagradost.cloudstream3.extractors.Watchx
import com.lagradost.cloudstream3.extractors.WcoStream
import com.lagradost.cloudstream3.extractors.Wibufile
import com.lagradost.cloudstream3.extractors.WishembedPro
import com.lagradost.cloudstream3.extractors.Wishfast
import com.lagradost.cloudstream3.extractors.Wishonly
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.Yipsu
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
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Techinmind
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.Jsoup
import java.net.URI
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

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
@Suppress("DEPRECATION")
data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
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
    MAGNET;

    // See https://www.iana.org/assignments/media-types/media-types.xhtml
    fun getMimeType(): String {
        return when (this) {
            VIDEO -> "video/mp4"
            M3U8 -> "application/x-mpegURL"
            DASH -> "application/dash+xml"
            TORRENT -> "application/x-bittorrent"
            MAGNET -> "application/x-bittorrent"
        }
    }
}

private fun inferTypeFromUrl(url: String): ExtractorLinkType {
    val path = try {
        URI(url).path
    } catch (_: Throwable) {
        // don't log magnet links as errors
        null
    }
    return when {
        path?.endsWith(".m3u8") == true -> ExtractorLinkType.M3U8
        path?.endsWith(".mpd") == true -> ExtractorLinkType.DASH
        path?.endsWith(".torrent") == true -> ExtractorLinkType.TORRENT
        url.startsWith("magnet:") -> ExtractorLinkType.MAGNET
        else -> ExtractorLinkType.VIDEO
    }
}

val INFER_TYPE: ExtractorLinkType? = null

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

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    initializer: suspend ExtractorLink.() -> Unit = { }
): ExtractorLink {

    @Suppress("DEPRECATION_ERROR")
    val builder =
        ExtractorLink(
            source = source,
            name = name,
            url = url,
            type = type ?: INFER_TYPE
        )

    builder.initializer()
    return builder
}

suspend fun newDrmExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    uuid: UUID,
    initializer: suspend DrmExtractorLink.() -> Unit = { }
): DrmExtractorLink {

    @Suppress("DEPRECATION_ERROR")
    val builder =
        DrmExtractorLink(
            source = source,
            name = name,
            url = url,
            uuid = uuid,
            type = type ?: INFER_TYPE
        )

    builder.initializer()
    return builder
}

/** Class holds extracted DRM media info to be passed to the player.
 * @property source Name of the media source, appears on player layout.
 * @property name Title of the media, appears on player layout.
 * @property url Url string of media file
 * @property referer Referer that will be used by network request.
 * @property quality Quality of the media file
 * @property headers Headers <String, String> map that will be used by network request.
 * @property extractorData Used for getExtractorVerifierJob()
 * @property type the type of the media, use [INFER_TYPE] if you want to auto infer the type from the url
 * @property kid  Base64 value of The KID element (Key Id) contains the identifier of the key associated with a license.
 * @property key Base64 value of Key to be used to decrypt the media file.
 * @property uuid Drm UUID [WIDEVINE_UUID], [PLAYREADY_UUID], [CLEARKEY_UUID] (by default) .. etc
 * @property kty Key type "oct" (octet sequence) by default
 * @property keyRequestParameters Parameters that will used to request the key.
 * @see newDrmExtractorLink
 * */
@Suppress("DEPRECATION")
open class DrmExtractorLink private constructor(
    override val source: String,
    override val name: String,
    override val url: String,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    open var kid: String? = null,
    open var key: String? = null,
    open var uuid: UUID,
    open var kty: String? = null,
    open var keyRequestParameters: HashMap<String, String>,
    open var licenseUrl: String? = null,
) : ExtractorLink(
    source, name, url, referer, quality, headers, extractorData, type
) {
    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
        kid: String? = null,
        key: String? = null,
        uuid: UUID = CLEARKEY_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        keyRequestParameters = keyRequestParameters,
        kty = kty,
        licenseUrl = licenseUrl,
    )

    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
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
        kid: String? = null,
        key: String? = null,
        uuid: UUID = CLEARKEY_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
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
        licenseUrl = licenseUrl,
    )
}

/** Class holds extracted media info to be passed to the player.
 * @property source Name of the media source, appears on player layout.
 * @property name Title of the media, appears on player layout.
 * @property url Url string of media file
 * @property referer Referer that will be used by network request.
 * @property quality Quality of the media file
 * @property headers Headers <String, String> map that will be used by network request.
 * @property extractorData Used for getExtractorVerifierJob()
 * @property type Extracted link type (Video, M3u8, Dash, Torrent or Magnet)
 * @see newExtractorLink
 * */
open class ExtractorLink
@Deprecated("Use newExtractorLink", level = DeprecationLevel.WARNING)
constructor(
    open val source: String,
    open val name: String,
    override val url: String,
    override var referer: String,
    open var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    open var extractorData: String? = null,
    open var type: ExtractorLinkType,
) : IDownloadableMinimum {
    val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    val isDash: Boolean get() = type == ExtractorLinkType.DASH

    // Cached video size
    private var videoSize: Long? = null

    /**
     * Get video size in bytes with one head request. Only available for ExtractorLinkType.Video
     * @param timeoutSeconds timeout of the head request.
     */
    suspend fun getVideoSize(timeoutSeconds: Long = 3L): Long? {
        // Content-Length is not applicable to other types of formats
        if (this.type != ExtractorLinkType.VIDEO) return null

        videoSize = videoSize ?: runCatching {
            val response =
                app.head(this.url, headers = headers, referer = referer, timeout = timeoutSeconds)
            response.headers["Content-Length"]?.toLong()
        }.getOrNull()

        return videoSize
    }

    @JsonIgnore
    fun getAllHeaders(): Map<String, String> {
        if (referer.isBlank()) {
            return headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            return headers + mapOf("referer" to referer)
        }
        return headers
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** the type of the media, use INFER_TYPE if you want to auto infer the type from the url */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob() */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url)
    )

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
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
    @Suppress("DEPRECATION_ERROR")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
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

    @Suppress("DEPRECATION")
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
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
@Throws(CancellationException::class)
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // Ensure this coroutine has not timed out
    coroutineScope { ensureActive() }

    val currentUrl = unshortenLinkSafe(url)
    val compareUrl = currentUrl.lowercase().replace(schemaStripRegex, "")

    // Iterate in reverse order so the new registered ExtractorApi takes priority
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (compareUrl.startsWith(extractor.mainUrl.replace(schemaStripRegex, ""))) {
            try {
                extractor.getUrl(currentUrl, referer, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
                // Rethrow if we have timed out
                if (e is CancellationException) {
                    throw e
                }
            }
            return true
        }
    }

    // this is to match mirror domains - like example.com, example.net
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (FuzzySearch.partialRatio(
                extractor.mainUrl,
                currentUrl
            ) > 80
        ) {
            try {
                extractor.getUrl(currentUrl, referer, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
                // Rethrow if we have timed out
                if (e is CancellationException) {
                    throw e
                }
            }
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
    StreamTapeXyz(),

    //mixdrop extractors
    MixDropBz(),
    MixDropCh(),
    MixDropTo(),
    MixDropAg(),

    MixDrop(),

    Mcloud(),
    XStreamCdn(),

    StreamSB(),
    Sblona(),
    Vidgomunimesb(),
    StreamSilk(),
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
    FourCX(),
    PlayRu(),
    FourPlayRu(),
    Pichive(),
    FourPichive(),
    HDMomPlayer(),
    HDPlayerSystem(),
    VideoSeyred(),
    PeaceMakerst(),
    HDStreamAble(),
    RapidVid(),
    TRsTX(),
    VidMoxy(),
    Sobreatsesuyp(),
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
    D0000d(),
    D000dCom(),
    DoodstreamCom(),
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
    FilemoonV2(),

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
    Voe1(),
    Tubeless(),
    Moviehab(),
    MoviehabNet(),
    Jeniusplay(),
    StreamoUpload(),
    Streamup(),

    GamoVideo(),
    Gdriveplayerapi(),
    Gdriveplayerapp(),
    Gdriveplayerfun(),
    Gdriveplayerio(),
    Gdriveplayerme(),
    Gdriveplayerbiz(),
    Gdriveplayerorg(),
    Gdriveplayerus(),
    Gdriveplayerco(),
    GoodstreamExtractor(),
    Gdriveplayer(),
    DatabaseGdrive(),
    DatabaseGdrive2(),
    Mediafire(),

    YoutubeExtractor(),
    YoutubeShortLinkExtractor(),
    YoutubeMobileExtractor(),
    YoutubeNoCookieExtractor(),
    Streamlare(),
    VidSrcExtractor(),
    VidSrcExtractor2(),
    VidSrcTo(),
    PlayLtXyz(),
    AStreamHub(),
    Vidplay(),
    VidplayOnline(),
    MyCloud(),
    MegaF(),

    Cda(),
    Dailymotion(),
    ByteShare(),
    Ztreamhub(),
    Rabbitstream(),
    Dokicloud(),
    Megacloud(),
    VidhideExtractor(),
    VidHidePro(),
    VidHidePro1(),
    VidHidePro2(),
    VidHidePro3(),
    VidHidePro4(),
    VidHidePro5(),
    VidHidePro6(),
    Dhtpre(),
    Dhcplay(),
    Smoothpre(),
    Peytonepre(),
    LuluStream(),
    Lulustream1(),
    Lulustream2(),
    StreamWishExtractor(),
    BigwarpIO(),
    BigwarpArt(),
    BgwpCC(),
    WishembedPro(),
    CdnwishCom(),
    FlaswishCom(),
    SfastwishCom(),
    Playerwish(),
    EmturbovidExtractor(),
    Vtbe(),
    EPlayExtractor(),
    Vidguardto(),
    Vidguardto1(),
    Vidguardto2(),
    Vidguardto3(),
    SecvideoOnline(),
    FsstOnline(),
    CsstOnline(),
    DsstOnline(),
    Simpulumlamerop(),
    Urochsunloath(),
    NathanFromSubject(),
    Yipsu(),
    MetaGnathTuggers(),
    Geodailymotion(),
    Mwish(),
    Dwish(),
    Ewish(),
    Kswplayer(),
    Wishfast(),
    Streamwish2(),
    Strwish(),
    Strwish2(),
    Awish(),
    Obeywish(),
    Jodwish(),
    Swhoi(),
    Multimovies(),
    UqloadsXyz(),
    Doodporn(),
    Asnwish(),
    Nekowish(),
    Nekostream(),
    Swdyu(),
    Wishonly(),
    Beastx(),
    Playerx(),
    AnimesagaStream(),
    Anplay(),
    Kinogeru(),
    Vidxstream(),
    Boltx(),
    Vectorx(),
    Boosterx(),
    Ds2play(),
    Ds2video(),
    Filegram(),
    InternetArchive(),
    VidStack(),
    GDMirrorbot(),
    Techinmind(),
    Server1uns(),
    VinovoSi(),
    VinovoTo(),
    CloudMailRu(),
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

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    //suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
    //    return safeAsync { getUrl(url, referer) }
    //}

    // this is the new extractorapi, override to add subtitles and stuff
    @Throws
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
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
