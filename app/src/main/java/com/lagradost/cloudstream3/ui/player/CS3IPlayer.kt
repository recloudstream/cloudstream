package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer.STATE_ENABLED
import androidx.media3.exoplayer.Renderer.STATE_STARTED
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.AppContextUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import com.lagradost.fetchbutton.aria2c.Aria2Starter
import com.lagradost.fetchbutton.aria2c.DownloadListener
import com.lagradost.fetchbutton.aria2c.DownloadStatusTell
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession

const val TAG = "CS3ExoPlayer"
const val PREFERRED_AUDIO_LANGUAGE_KEY = "preferred_audio_language"

/** toleranceBeforeUs – The maximum time that the actual position seeked to may precede the
 * requested seek position, in microseconds. Must be non-negative. */
const val toleranceBeforeUs = 300_000L

/**
 * toleranceAfterUs – The maximum time that the actual position seeked to may exceed the requested
 * seek position, in microseconds. Must be non-negative.
 */
const val toleranceAfterUs = 300_000L

@OptIn(UnstableApi::class)
class CS3IPlayer : IPlayer {
    private var isPlaying = false
    private var exoPlayer: ExoPlayer? = null
        set(value) {
            // If the old value is not null then the player has not been properly released.
            debugAssert(
                { field != null && value != null },
                { "Previous player instance should be released!" })
            field = value
        }

    var cacheSize = 0L
    var simpleCacheSize = 0L
    var videoBufferMs = 0L

    val imageGenerator = IPreviewGenerator.new()

    private val seekActionTime = 30000L

    private var ignoreSSL: Boolean = true
    private var playBackSpeed: Float = 1.0f

    private var lastMuteVolume: Float = 1.0f

    private var currentLink: ExtractorLink? = null
    private var currentDownloadedFile: ExtractorUri? = null
    private var hasUsedFirstRender = false

    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private val subtitleHelper = PlayerSubtitleHelper()

    /**
     * This is a way to combine the MediaItem and its duration for the concatenating MediaSource.
     * @param durationUs does not matter if only one slice is present, since it will not concatenate
     * */
    data class MediaItemSlice(
        val mediaItem: MediaItem,
        val durationUs: Long,
        val drm: DrmMetadata? = null
    )

    data class DrmMetadata(
        val kid: String,
        val key: String,
        val uuid: UUID,
        val kty: String,
        val keyRequestParameters: HashMap<String, String>,
    )

    override fun getDuration(): Long? = exoPlayer?.duration
    override fun getPosition(): Long? = exoPlayer?.currentPosition
    override fun getIsPlaying(): Boolean = isPlaying
    override fun getPlaybackSpeed(): Float = playBackSpeed

    /**
     * Tracks reported to be used by exoplayer, since sometimes it has a mind of it's own when selecting subs.
     * String = id (without exoplayer track number)
     * Boolean = if it's active
     * */
    private var playerSelectedSubtitleTracks = listOf<Pair<String, Boolean>>()
    private var requestedListeningPercentages: List<Int>? = null

    private var eventHandler: ((PlayerEvent) -> Unit)? = null

    fun event(event: PlayerEvent) {
        eventHandler?.invoke(event)
    }

    override fun releaseCallbacks() {
        eventHandler = null
    }

    override fun initCallbacks(
        eventHandler: ((PlayerEvent) -> Unit),
        requestedListeningPercentages: List<Int>?,
    ) {
        this.requestedListeningPercentages = requestedListeningPercentages
        this.eventHandler = eventHandler
    }

    // I know, this is not a perfect solution, however it works for fixing subs
    private fun reloadSubs() {
        exoPlayer?.applicationLooper?.let {
            try {
                Handler(it).post {
                    try {
                        seekTime(1L, source = PlayerEventSource.Player)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    fun String.stripTrackId(): String {
        return this.replace(Regex("""^\d+:"""), "")
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleHelper.initSubtitles(subView, subHolder, style)
    }

    override fun getPreview(fraction: Float): Bitmap? {
        return imageGenerator.getPreviewImage(fraction)
    }

    override fun hasPreview(): Boolean {
        return imageGenerator.hasPreview()
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?,
        preview: Boolean,
    ) {
        Log.i(TAG, "loadPlayer")
        if (sameEpisode) {
            saveData()
        } else {
            currentSubtitles = subtitle
            playbackPosition = 0
        }

        startPosition?.let {
            playbackPosition = it
        }

        // we want autoplay because of TV and UX
        isPlaying = autoPlay ?: isPlaying

        // release the current exoplayer and cache
        releasePlayer()

        if (link != null) {
            // only video support atm
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(link, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }

            loadOnlinePlayer(context, link)
        } else if (data != null) {
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(context, data, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }
            loadOfflinePlayer(context, data)
        } else {
            throw IllegalArgumentException("Requires link or uri")
        }

    }

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        Log.i(TAG, "setActiveSubtitles ${subtitles.size}")
        subtitleHelper.setAllSubtitles(subtitles)
    }

    private var currentSubtitles: SubtitleData? = null

    private fun List<Tracks.Group>.getTrack(id: String?): Pair<TrackGroup, Int>? {
        if (id == null) return null
        // This beast of an expression does:
        // 1. Filter all audio tracks
        // 2. Get all formats in said audio tacks
        // 3. Gets all ids of the formats
        // 4. Filters to find the first audio track with the same id as the audio track we are looking for
        // 5. Returns the media group and the index of the audio track in the group
        return this.firstNotNullOfOrNull { group ->
            (0 until group.mediaTrackGroup.length).map {
                group.getTrackFormat(it) to it
            }.firstOrNull {
                // The format id system is "trackNumber:trackID"
                // The track number is not generated by us so we filter it out
                it.first.id?.stripTrackId() == id
            }
                ?.let { group.mediaTrackGroup to it.second }
        }
    }

    override fun setMaxVideoSize(width: Int, height: Int, id: String?) {
        if (id != null) {
            val videoTrack =
                exoPlayer?.currentTracks?.groups?.filter { it.type == TRACK_TYPE_VIDEO }
                    ?.getTrack(id)

            if (videoTrack != null) {
                exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setOverrideForType(
                        TrackSelectionOverride(
                            videoTrack.first,
                            videoTrack.second
                        )
                    )
                    ?.build()
                    ?: return
                return
            }
        }

        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setMaxVideoSize(width, height)
            ?.build()
            ?: return
    }

    override fun setPreferredAudioTrack(trackLanguage: String?, id: String?) {
        preferredAudioTrackLanguage = trackLanguage

        if (id != null) {
            val audioTrack =
                exoPlayer?.currentTracks?.groups?.filter { it.type == TRACK_TYPE_AUDIO }
                    ?.getTrack(id)

            if (audioTrack != null) {
                exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setOverrideForType(
                        TrackSelectionOverride(
                            audioTrack.first,
                            audioTrack.second
                        )
                    )
                    ?.build()
                    ?: return
                return
            }
        }

        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setPreferredAudioLanguage(trackLanguage)
            ?.build()
            ?: return
    }


    /**
     * Gets all supported formats in a list
     * */
    private fun List<Tracks.Group>.getFormats(): List<Pair<Format, Int>> {
        return this.map {
            it.getFormats()
        }.flatten()
    }

    private fun Tracks.Group.getFormats(): List<Pair<Format, Int>> {
        return (0 until this.mediaTrackGroup.length).mapNotNull { i ->
            if (this.isSupported)
                this.mediaTrackGroup.getFormat(i) to i
            else null
        }
    }

    private fun Format.toAudioTrack(): AudioTrack {
        return AudioTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language
        )
    }

    private fun Format.toSubtitleTrack(): TextTrack {
        return TextTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.sampleMimeType
        )
    }

    private fun Format.toVideoTrack(): VideoTrack {
        return VideoTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.width,
            this.height,
        )
    }

    override fun getVideoTracks(): CurrentTracks {
        val allTracks = exoPlayer?.currentTracks?.groups ?: emptyList()
        val videoTracks = allTracks.filter { it.type == TRACK_TYPE_VIDEO }
            .getFormats()
            .map { it.first.toVideoTrack() }
        val audioTracks = allTracks.filter { it.type == TRACK_TYPE_AUDIO }.getFormats()
            .map { it.first.toAudioTrack() }

        val textTracks = allTracks.filter { it.type == TRACK_TYPE_TEXT }.getFormats()
            .map { it.first.toSubtitleTrack() }

        val currentTextTracks = textTracks.filter { track ->
            playerSelectedSubtitleTracks.any { it.second && it.first == track.id }
        }

        return CurrentTracks(
            exoPlayer?.videoFormat?.toVideoTrack(),
            exoPlayer?.audioFormat?.toAudioTrack(),
            currentTextTracks,
            videoTracks,
            audioTracks,
            textTracks
        )
    }

    /**
     * @return True if the player should be reloaded
     * */
    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        Log.i(TAG, "setPreferredSubtitles init $subtitle")
        currentSubtitles = subtitle

        fun getTextTrack(id: String) =
            exoPlayer?.currentTracks?.groups?.filter { it.type == TRACK_TYPE_TEXT }
                ?.getTrack(id)

        return (exoPlayer?.trackSelector as? DefaultTrackSelector?)?.let { trackSelector ->
            if (subtitle == null) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(TRACK_TYPE_TEXT)
                )
            } else {
                when (subtitleHelper.subtitleStatus(subtitle)) {
                    SubtitleStatus.REQUIRES_RELOAD -> {
                        Log.i(TAG, "setPreferredSubtitles REQUIRES_RELOAD")
                        return@let true
                    }

                    SubtitleStatus.IS_ACTIVE -> {
                        Log.i(TAG, "setPreferredSubtitles IS_ACTIVE")

                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .apply {
                                    val track = getTextTrack(subtitle.getId())
                                    if (track != null) {
                                        setTrackTypeDisabled(TRACK_TYPE_TEXT, false)
                                        setOverrideForType(
                                            TrackSelectionOverride(
                                                track.first,
                                                track.second
                                            )
                                        )
                                    }
                                }
                        )

                        // ugliest code I have written, it seeks 1ms to *update* the subtitles
                        //exoPlayer?.applicationLooper?.let {
                        //    Handler(it).postDelayed({
                        //        seekTime(1L)
                        //    }, 1)
                        //}
                    }

                    SubtitleStatus.NOT_FOUND -> {
                        Log.i(TAG, "setPreferredSubtitles NOT_FOUND")
                        return@let true
                    }
                }
            }
            return false
        } ?: false
    }

    private var currentSubtitleOffset: Long = 0

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffset = offset
        CustomDecoder.subtitleOffset = offset
        if (currentTextRenderer?.state == STATE_ENABLED || currentTextRenderer?.state == STATE_STARTED) {
            exoPlayer?.currentPosition?.let { pos ->
                // This seems to properly refresh all subtitles
                // It needs to be done as all subtitle cues with timings are pre-processed
                currentTextRenderer?.resetPosition(pos)
            }
        }
    }

    override fun getSubtitleOffset(): Long {
        return currentSubtitleOffset
    }

    override fun getSubtitleCues(): List<SubtitleCue> {
        return currentSubtitleDecoder?.getSubtitleCues() ?: emptyList()
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            playerSelectedSubtitleTracks.any { (id, isSelected) ->
                isSelected && sub.getId() == id
            }
        }
    }

    override fun getAspectRatio(): Rational? {
        return exoPlayer?.videoFormat?.let { format ->
            Rational(format.width, format.height)
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    override fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentMediaItemIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer(saveTime: Boolean = true) {
        Log.i(TAG, "releasePlayer")
        eventLooperIndex += 1
        if (saveTime)
            updatedTime()

        exoPlayer?.apply {
            playWhenReady = false
            stop()
            release()
        }
        //simpleCache?.release()

        exoPlayer = null
        //simpleCache = null
    }

    override fun onStop() {
        Log.i(TAG, "onStop")

        saveData()
        handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        //releasePlayer()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        saveData()
        handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        //releasePlayer()
    }

    override fun onResume(context: Context) {
        if (exoPlayer == null)
            reloadPlayer(context)
    }

    override fun release() {
        imageGenerator.release()
        Torrent.release()
        Torrent.deleteAllOldFiles()
        releasePlayer()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        playBackSpeed = speed
    }

    companion object {
        /**
         * Setting this variable is permanent across app sessions.
         **/
        var preferredAudioTrackLanguage: String? = null
            get() {
                return field ?: getKey(
                    "$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY",
                    field
                )?.also {
                    field = it
                }
            }
            set(value) {
                setKey("$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY", value)
                field = value
            }

        private var simpleCache: SimpleCache? = null

        var requestSubtitleUpdate: (() -> Unit)? = null

        private fun createOnlineSource(headers: Map<String, String>): HttpDataSource.Factory {
            val source = OkHttpDataSource.Factory(app.baseClient).setUserAgent(USER_AGENT)
            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        private fun createOnlineSource(link: ExtractorLink): HttpDataSource.Factory {
            val provider = getApiFromNameNull(link.source)
            val interceptor = provider?.getVideoInterceptor(link)

            val source = if (interceptor == null) {
                DefaultHttpDataSource.Factory() //TODO USE app.baseClient
                    .setUserAgent(USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)   //https://stackoverflow.com/questions/69040127/error-code-io-bad-http-status-exoplayer-android
            } else {
                val client = app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
                OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
            }

            // Do no include empty referer, if the provider wants those they can use the header map.
            val refererMap =
                if (link.referer.isBlank()) emptyMap() else mapOf("referer" to link.referer)
            val headers = mapOf(
                "accept" to "*/*",
                "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-fetch-user" to "?1",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-dest" to "video"
            ) + refererMap + link.headers // Adds the headers from the provider, e.g Authorization

            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSource.Factory(
                this,
                DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
            )
        }

        private fun getCache(context: Context, cacheSize: Long): SimpleCache? {
            return try {
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    File(
                        context.cacheDir, "exoplayer"
                    ).also { deleteFileOnExit(it) }, // Ensures always fresh file
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    databaseProvider
                )
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        private fun getMediaItemBuilder(mimeType: String):
                MediaItem.Builder {
            return MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)
        }

        private fun getMediaItem(mimeType: String, uri: Uri): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(uri).build()
        }

        private fun getMediaItem(mimeType: String, url: String): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(url).build()
        }

        private fun getTrackSelector(context: Context, maxVideoHeight: Int?): TrackSelector {
            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters = trackSelector.buildUponParameters()
                // This will not force higher quality videos to fail
                // but will make the m3u8 pick the correct preferred
                .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight ?: Int.MAX_VALUE)
                .setPreferredAudioLanguage(null)
                .build()
            return trackSelector
        }

        private var currentSubtitleDecoder: CustomSubtitleDecoderFactory? = null
        private var currentTextRenderer: TextRenderer? = null

        private fun buildExoPlayer(
            context: Context,
            mediaItemSlices: List<MediaItemSlice>,
            subSources: List<SingleSampleMediaSource>,
            currentWindow: Int,
            playbackPosition: Long,
            playBackSpeed: Float,
            subtitleOffset: Long,
            cacheSize: Long,
            videoBufferMs: Long,
            playWhenReady: Boolean = true,
            cacheFactory: CacheDataSource.Factory? = null,
            trackSelector: TrackSelector? = null,
            /**
             * Sets the m3u8 preferred video quality, will not force stop anything with higher quality.
             * Does not work if trackSelector is defined.
             **/
            maxVideoHeight: Int? = null
        ): ExoPlayer {
            val exoPlayerBuilder =
                ExoPlayer.Builder(context)
                    .setRenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput ->

                        NextRenderersFactory(context).apply {
                            setEnableDecoderFallback(true)
                            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        }.createRenderers(
                            eventHandler,
                            videoRendererEventListener,
                            audioRendererEventListener,
                            textRendererOutput,
                            metadataRendererOutput
                        ).map {
                            if (it is TextRenderer) {
                                CustomDecoder.subtitleOffset = subtitleOffset
                                val decoder = CustomSubtitleDecoderFactory()
                                val currentTextRenderer = TextRenderer(
                                    textRendererOutput,
                                    eventHandler.looper,
                                    decoder
                                ).apply {
                                    // Required to make the decoder work with old subtitles
                                    // Upgrade CustomSubtitleDecoderFactory when media3 supports it
                                    experimentalSetLegacyDecodingEnabled(true)
                                }.also { renderer ->
                                    this.currentTextRenderer = renderer
                                    this.currentSubtitleDecoder = decoder
                                }
                                currentTextRenderer
                            } else
                                it
                        }.toTypedArray()
                    }
                    .setTrackSelector(
                        trackSelector ?: getTrackSelector(
                            context,
                            maxVideoHeight
                        )
                    )
                    // Allows any seeking to be +- 0.3s to allow for faster seeking
                    .setSeekParameters(SeekParameters(toleranceBeforeUs, toleranceAfterUs))
                    .setLoadControl(
                        DefaultLoadControl.Builder()
                            .setTargetBufferBytes(
                                if (cacheSize <= 0) {
                                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES
                                } else {
                                    if (cacheSize > Int.MAX_VALUE) Int.MAX_VALUE else cacheSize.toInt()
                                }
                            )
                            .setBackBuffer(
                                30000,
                                true
                            )
                            .setBufferDurationsMs(
                                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                                if (videoBufferMs <= 0) {
                                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
                                } else {
                                    videoBufferMs.toInt()
                                },
                                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                            ).build()
                    )


            val factory =
                if (cacheFactory == null) DefaultMediaSourceFactory(context)
                else DefaultMediaSourceFactory(cacheFactory)

            // If there is only one item then treat it as normal, if multiple: concatenate the items.
            val videoMediaSource = if (mediaItemSlices.size == 1) {
                val item = mediaItemSlices.first()

                item.drm?.let { drm ->
                    val drmCallback =
                        LocalMediaDrmCallback("{\"keys\":[{\"kty\":\"${drm.kty}\",\"k\":\"${drm.key}\",\"kid\":\"${drm.kid}\"}],\"type\":\"temporary\"}".toByteArray())
                    val manager = DefaultDrmSessionManager.Builder()
                        .setPlayClearSamplesWithoutKeys(true)
                        .setMultiSession(false)
                        .setKeyRequestParameters(drm.keyRequestParameters)
                        .setUuidAndExoMediaDrmProvider(drm.uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(drmCallback)
                    val manifestDataSourceFactory = DefaultHttpDataSource.Factory()

                    DashMediaSource.Factory(manifestDataSourceFactory)
                        .setDrmSessionManagerProvider { manager }
                        .createMediaSource(item.mediaItem)
                } ?: run {
                    factory.createMediaSource(item.mediaItem)
                }
            } else {
                val source = ConcatenatingMediaSource()
                mediaItemSlices.map { item ->
                    source.addMediaSource(
                        // The duration MUST be known for it to work properly, see https://github.com/google/ExoPlayer/issues/4727
                        ClippingMediaSource(
                            factory.createMediaSource(item.mediaItem),
                            item.durationUs
                        )
                    )
                }
                source
            }

            //println("PLAYBACK POS $playbackPosition")
            return exoPlayerBuilder.build().apply {
                setPlayWhenReady(playWhenReady)
                seekTo(currentWindow, playbackPosition)
                setMediaSource(
                    MergingMediaSource(
                        videoMediaSource, *subSources.toTypedArray()
                    ),
                    playbackPosition
                )
                setHandleAudioBecomingNoisy(true)
                setPlaybackSpeed(playBackSpeed)
            }
        }
    }

    private fun getCurrentTimestamp(writePosition: Long? = null): EpisodeSkip.SkipStamp? {
        val position = writePosition ?: this@CS3IPlayer.getPosition() ?: return null
        for (lastTimeStamp in lastTimeStamps) {
            if (lastTimeStamp.startMs <= position && (position + (toleranceBeforeUs / 1000L) + 1) < lastTimeStamp.endMs) {
                return lastTimeStamp
            }
        }
        return null
    }

    fun updatedTime(
        writePosition: Long? = null,
        source: PlayerEventSource = PlayerEventSource.Player
    ) {
        val position = writePosition ?: exoPlayer?.currentPosition

        getCurrentTimestamp(position)?.let { timestamp ->
            event(TimestampInvokedEvent(timestamp, source))
        }

        val duration = exoPlayer?.contentDuration
        if (duration != null && position != null) {
            event(
                PositionEvent(
                    source,
                    fromMs = exoPlayer?.currentPosition ?: 0,
                    position,
                    duration
                )
            )
        }
    }

    override fun seekTime(time: Long, source: PlayerEventSource) {
        exoPlayer?.seekTime(time, source)
    }

    override fun seekTo(time: Long, source: PlayerEventSource) {
        updatedTime(time, source)
        exoPlayer?.seekTo(time)
    }

    private fun ExoPlayer.seekTime(time: Long, source: PlayerEventSource) {
        updatedTime(currentPosition + time, source)
        seekTo(currentPosition + time)
    }

    override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
        Log.i(TAG, "handleEvent ${event.name}")
        try {
            exoPlayer?.apply {
                when (event) {
                    CSPlayerEvent.Play -> {
                        event(PlayEvent(source))
                        play()
                    }

                    CSPlayerEvent.Pause -> {
                        event(PauseEvent(source))
                        pause()
                    }

                    CSPlayerEvent.ToggleMute -> {
                        if (volume <= 0) {
                            //is muted
                            volume = lastMuteVolume
                        } else {
                            // is not muted
                            lastMuteVolume = volume
                            volume = 0f
                        }
                    }

                    CSPlayerEvent.PlayPauseToggle -> {
                        if (isPlaying) {
                            handleEvent(CSPlayerEvent.Pause, source)
                        } else {
                            handleEvent(CSPlayerEvent.Play, source)
                        }
                    }

                    CSPlayerEvent.SeekForward -> seekTime(seekActionTime, source)

                    CSPlayerEvent.SeekBack -> seekTime(-seekActionTime, source)

                    CSPlayerEvent.Restart -> seekTo(0, source)

                    CSPlayerEvent.NextEpisode -> event(
                        EpisodeSeekEvent(
                            offset = 1,
                            source = source
                        )
                    )

                    CSPlayerEvent.PrevEpisode -> event(
                        EpisodeSeekEvent(
                            offset = -1,
                            source = source
                        )
                    )

                    CSPlayerEvent.SkipCurrentChapter -> {
                        //val dur = this@CS3IPlayer.getDuration() ?: return@apply
                        getCurrentTimestamp()?.let { lastTimeStamp ->
                            if (lastTimeStamp.skipToNextEpisode) {
                                handleEvent(CSPlayerEvent.NextEpisode, source)
                            } else {
                                seekTo(lastTimeStamp.endMs + 1L)
                            }
                            event(TimestampSkippedEvent(timestamp = lastTimeStamp, source = source))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleEvent error", t)
            event(ErrorEvent(t))
        }
    }

    // we want to push metadata when loading torrents, so we just set up a looper that loops until
    // the index changes, this way only 1 looper is active at a time, and modifying eventLooperIndex
    // will kill any active loopers
    private var eventLooperIndex = 0
    private fun torrentEventLooper() = ioSafe {
        eventLooperIndex += 1
        val currentIndex = eventLooperIndex
        while (currentIndex == eventLooperIndex) {
            Aria2Starter.refresh()
            DownloadListener.sessionIdToGid[activeTorrentRequest?.requestId]?.let { gid ->
                val metadata = DownloadListener.getInfo(gid)
                event(
                    DownloadEvent(
                        downloadedBytes = metadata.downloadedLength,
                        downloadSpeed = metadata.downloadSpeed,
                        totalBytes = metadata.totalLength,
                        connections = metadata.items.sumOf { it.connections }
                    )
                )
                when (metadata.status) {
                    DownloadStatusTell.Waiting -> delay(500)
                    DownloadStatusTell.Paused -> delay(1000)
                    DownloadStatusTell.Error, DownloadStatusTell.Removed, DownloadStatusTell.Complete -> return@ioSafe
                    null, DownloadStatusTell.Active -> Unit
                }
            }
            delay(100)
        }
    }

    private fun loadExo(
        context: Context,
        mediaSlices: List<MediaItemSlice>,
        subSources: List<SingleSampleMediaSource>,
        cacheFactory: CacheDataSource.Factory? = null
    ) {
        Log.i(TAG, "loadExo")
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val maxVideoHeight = settingsManager.getInt(
            context.getString(if (context.isUsingMobileData()) com.lagradost.cloudstream3.R.string.quality_pref_mobile_data_key else com.lagradost.cloudstream3.R.string.quality_pref_key),
            Int.MAX_VALUE
        )

        try {
            hasUsedFirstRender = false

            // ye this has to be a val for whatever reason
            // this makes no sense
            exoPlayer = buildExoPlayer(
                context,
                mediaSlices,
                subSources,
                currentWindow,
                playbackPosition,
                playBackSpeed,
                cacheSize = cacheSize,
                videoBufferMs = videoBufferMs,
                playWhenReady = isPlaying, // this keep the current state of the player
                cacheFactory = cacheFactory,
                subtitleOffset = currentSubtitleOffset,
                maxVideoHeight = maxVideoHeight
            )

            requestSubtitleUpdate = ::reloadSubs

            event(PlayerAttachedEvent(exoPlayer))
            exoPlayer?.prepare()

            exoPlayer?.let { exo ->
                event(StatusEvent(CSPlayerLoading.IsBuffering, CSPlayerLoading.IsBuffering))
                isPlaying = exo.isPlaying
            }

            // we want to avoid an empty exoplayer from sending events
            // this is because we need PlayerAttachedEvent to be called to render the UI
            // but don't really want the rest like Player.STATE_ENDED calling next episode
            if (mediaSlices.isEmpty() && subSources.isEmpty()) {
                return
            }

            exoPlayer?.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    normalSafeApiCall {
                        val textTracks = tracks.groups.filter { it.type == TRACK_TYPE_TEXT }

                        playerSelectedSubtitleTracks =
                            textTracks.map { group ->
                                group.getFormats().mapNotNull { (format, _) ->
                                    (format.id?.stripTrackId()
                                        ?: return@mapNotNull null) to group.isSelected
                                }
                            }.flatten()

                        val exoPlayerReportedTracks =
                            tracks.groups.filter { it.type == TRACK_TYPE_TEXT }.getFormats()
                                .mapNotNull { (format, _) ->
                                    // Filter out non subs, already used subs and subs without languages
                                    if (format.id == null ||
                                        format.language == null ||
                                        format.language?.startsWith("-") == true
                                    ) return@mapNotNull null

                                    return@mapNotNull SubtitleData(
                                        // Nicer looking displayed names
                                        fromTwoLettersToLanguage(format.language!!)
                                            ?: format.language!!,
                                        // See setPreferredTextLanguage
                                        format.id!!.stripTrackId(),
                                        SubtitleOrigin.EMBEDDED_IN_VIDEO,
                                        format.sampleMimeType ?: MimeTypes.APPLICATION_SUBRIP,
                                        emptyMap(),
                                        format.language
                                    )
                                }

                        event(EmbeddedSubtitlesFetchedEvent(tracks = exoPlayerReportedTracks))
                        event(TracksChangedEvent())
                        event(SubtitlesUpdatedEvent())
                    }
                }

                //fixme: Use onPlaybackStateChanged(int) and onPlayWhenReadyChanged(boolean, int) instead.
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    exoPlayer?.let { exo ->
                        event(
                            StatusEvent(
                                wasPlaying = if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused,
                                isPlaying = if (playbackState == Player.STATE_BUFFERING) CSPlayerLoading.IsBuffering else if (exo.isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                            )
                        )
                        isPlaying = exo.isPlaying
                    }

                    when (playbackState) {
                        Player.STATE_READY -> {
                            onRenderFirst()
                        }

                        else -> {}
                    }


                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {

                            }

                            Player.STATE_ENDED -> {
                                event(VideoEndedEvent())
                            }

                            Player.STATE_BUFFERING -> {
                                updatedTime(source = PlayerEventSource.Player)
                            }

                            Player.STATE_IDLE -> {

                            }

                            else -> Unit
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val request = activeTorrentRequest

                    // if we are loading an torrent, then we will get these errors, in that case
                    // we just treat it as buffering
                    if (request != null &&
                        (error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                                || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                                || error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                                || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                                || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                                || error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                                || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                    ) {
                        val gid = DownloadListener.sessionIdToGid[request.requestId]
                        if (gid == null) {
                            event(ErrorEvent(error))
                            super.onPlayerError(error)
                            return
                        }

                        event(
                            StatusEvent(
                                wasPlaying = CSPlayerLoading.IsPlaying,
                                isPlaying = CSPlayerLoading.IsBuffering
                            )
                        )

                        // `isPlaying = true` as we want to autoplay, because errors only happends when
                        // we are not paused

                        // we have manually deleted the file without notifying the Aria2 instance
                        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                            Aria2Starter.delete(gid, request.requestId)
                            isPlaying = true
                            loadOnlinePlayer(context, request.request)
                            return
                        }

                        // give information when buffering, and after a set timeout we run again
                        Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({
                            // if we have released the player while it is waiting, then do nothing
                            if (exoPlayer == null) return@postDelayed
                            playbackPosition = exoPlayer?.currentPosition ?: 0L
                            isPlaying = true
                            loadOnlinePlayer(context, request.request, retry = true)
                        }, 3000)
                        return
                    }

                    // If the Network fails then ignore the exception if the duration is set.
                    // This is to switch mirrors automatically if the stream has not been fetched, but
                    // allow playing the buffer without internet as then the duration is fetched.
                    when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                                && exoPlayer?.duration != TIME_UNSET -> {
                            exoPlayer?.prepare()
                        }

                        error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                            // Re-initialize player at the current live window default position.
                            exoPlayer?.seekToDefaultPosition()
                            exoPlayer?.prepare()
                        }

                        else -> {
                            event(ErrorEvent(error))
                        }
                    }

                    super.onPlayerError(error)
                }

                //override fun onCues(cues: MutableList<Cue>) {
                //    super.onCues(cues.map { cue -> cue.buildUpon().setText("Hello world").setSize(Cue.DIMEN_UNSET).build() })
                //}

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    if (isPlaying) {
                        event(RequestAudioFocusEvent())
                        onRenderFirst()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    when (playbackState) {
                        Player.STATE_READY -> {

                        }

                        Player.STATE_ENDED -> {
                            // Resets subtitle delay on ended video
                            setSubtitleOffset(0)

                            // Only play next episode if autoplay is on (default)
                            if (PreferenceManager.getDefaultSharedPreferences(context)
                                    ?.getBoolean(
                                        context.getString(com.lagradost.cloudstream3.R.string.autoplay_next_key),
                                        true
                                    ) == true
                            ) {
                                handleEvent(
                                    CSPlayerEvent.NextEpisode,
                                    source = PlayerEventSource.Player
                                )
                            }
                        }

                        Player.STATE_BUFFERING -> {
                            updatedTime(source = PlayerEventSource.Player)
                        }

                        Player.STATE_IDLE -> {
                            // IDLE
                        }

                        else -> Unit
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    event(ResizedEvent(height = videoSize.height, width = videoSize.width))
                }

                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    onRenderFirst()
                    updatedTime(source = PlayerEventSource.Player)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "loadExo error", t)
            event(ErrorEvent(t))
        }
    }

    private var lastTimeStamps: List<EpisodeSkip.SkipStamp> = emptyList()

    override fun addTimeStamps(timeStamps: List<EpisodeSkip.SkipStamp>) {
        lastTimeStamps = timeStamps
        timeStamps.forEach { timestamp ->
            exoPlayer?.createMessage { _, _ ->
                updatedTime(source = PlayerEventSource.Player)
                //if (payload is EpisodeSkip.SkipStamp) // this should always be true
                //    onTimestampInvoked?.invoke(payload)
            }
                ?.setLooper(Looper.getMainLooper())
                ?.setPosition(timestamp.startMs)
                //?.setPayload(timestamp)
                ?.setDeleteAfterDelivery(false)
                ?.send()
        }
        updatedTime(source = PlayerEventSource.Player)
    }

    fun onRenderFirst() {
        if (hasUsedFirstRender) { // this insures that we only call this once per player load
            return
        }
        Log.i(TAG, "Rendered first frame")
        hasUsedFirstRender = true
        val invalid = exoPlayer?.duration?.let { duration ->
            // Only errors short playback when not playing downloaded files
            duration < 20_000L && currentDownloadedFile == null
                    // Concatenated sources (non 1 periodCount) bypasses the invalid check as exoPlayer.duration gives only the current period
                    // If you can get the total time that'd be better, but this is already niche.
                    && exoPlayer?.currentTimeline?.periodCount == 1
                    && exoPlayer?.isCurrentMediaItemLive != true
        } ?: false

        if (invalid) {
            releasePlayer(saveTime = false)
            event(ErrorEvent(InvalidFileException("Too short playback")))
            return
        }

        setPreferredSubtitles(currentSubtitles)
        val format = exoPlayer?.videoFormat
        val width = format?.width
        val height = format?.height
        if (height != null && width != null) {
            event(ResizedEvent(width = width, height = height))
            updatedTime()
            exoPlayer?.apply {
                requestedListeningPercentages?.forEach { percentage ->
                    createMessage { _, _ ->
                        updatedTime()
                    }
                        .setLooper(Looper.getMainLooper())
                        .setPosition(contentDuration * percentage / 100)
                        //   .setPayload(customPayloadData)
                        .setDeleteAfterDelivery(false)
                        .send()
                }
            }
        }
    }

    private fun loadOfflinePlayer(context: Context, data: ExtractorUri) {
        Log.i(TAG, "loadOfflinePlayer")
        try {
            currentDownloadedFile = data

            val mediaItem = getMediaItem(MimeTypes.VIDEO_MP4, data.uri)
            val offlineSourceFactory = context.createOfflineSource()
            val onlineSourceFactory = createOnlineSource(emptyMap())

            val (subSources, activeSubtitles) = getSubSources(
                onlineSourceFactory = onlineSourceFactory,
                offlineSourceFactory = offlineSourceFactory,
                subtitleHelper,
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())
            loadExo(context, listOf(MediaItemSlice(mediaItem, Long.MIN_VALUE)), subSources)
        } catch (t: Throwable) {
            Log.e(TAG, "loadOfflinePlayer error", t)
            event(ErrorEvent(t))
        }
    }

    private fun getSubSources(
        onlineSourceFactory: HttpDataSource.Factory?,
        offlineSourceFactory: DataSource.Factory?,
        subHelper: PlayerSubtitleHelper,
    ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
        val activeSubtitles = ArrayList<SubtitleData>()
        val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.getFixedUrl()))
                .setMimeType(sub.mimeType)
                .setLanguage("_${sub.name}")
                .setId(sub.getId())
                .setSelectionFlags(0)
                .build()
            when (sub.origin) {
                SubtitleOrigin.DOWNLOADED_FILE -> {
                    if (offlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(offlineSourceFactory)
                            .createMediaSource(subConfig, TIME_UNSET)
                    } else {
                        null
                    }
                }

                SubtitleOrigin.URL -> {
                    if (onlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(onlineSourceFactory.apply {
                            if (sub.headers.isNotEmpty())
                                this.setDefaultRequestProperties(sub.headers)
                        })
                            .createMediaSource(subConfig, TIME_UNSET)
                    } else {
                        null
                    }
                }

                SubtitleOrigin.EMBEDDED_IN_VIDEO -> {
                    if (offlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(offlineSourceFactory)
                            .createMediaSource(subConfig, TIME_UNSET)
                    } else {
                        null
                    }
                }
            }
        }
        return Pair(subSources, activeSubtitles)
    }

    override fun isActive(): Boolean {
        return exoPlayer != null
    }

    var activeTorrentRequest: TorrentRequest? = null

    @MainThread
    private fun loadTorrent(context: Context, link: ExtractorLink) {
        ioSafe {
            // we check exoPlayer a lot here, and that is because we don't want to load exo after
            // the user has left the player, in the case that the user click back when this is
            // happening
            try {
                if (exoPlayer == null) return@ioSafe
                val request = Torrent.loadTorrent(link, eventHandler)
                if (exoPlayer == null) return@ioSafe
                activeTorrentRequest = request
                runOnMainThread {
                    if (exoPlayer == null) return@runOnMainThread
                    releasePlayer()
                    loadOfflinePlayer(context, request.data)
                    torrentEventLooper()
                }
            } catch (t: Throwable) {
                event(ErrorEvent(t))
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @MainThread
    private fun loadOnlinePlayer(context: Context, link: ExtractorLink, retry: Boolean = false) {
        Log.i(TAG, "loadOnlinePlayer $link")
        try {
            activeTorrentRequest = null
            val mime = when (link.type) {
                ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                ExtractorLinkType.VIDEO -> MimeTypes.VIDEO_MP4
                ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET -> {
                    // we check settings first, todo cleanup
                    val default = TvType.entries.toTypedArray()
                        .sorted()
                        .filter { it != TvType.NSFW }
                        .map { it.ordinal }

                    val defaultSet = default.map { it.toString() }.toSet()
                    val currentPrefMedia = try {
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .getStringSet(
                                context.getString(R.string.prefer_media_type_key),
                                defaultSet
                            )
                            ?.mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }
                    } catch (e: Throwable) {
                        null
                    } ?: default

                    if (!currentPrefMedia.contains(TvType.Torrent.ordinal)) {
                        event(ErrorEvent(ErrorLoadingException("Preferred media do not contain torrent")))
                        return
                    }

                    if (Torrent.hasAcceptedTorrentForThisSession == false) {
                        event(ErrorEvent(ErrorLoadingException("Not accepted torrent")))
                        return
                    }
                    // load the initial UI, we require an exoPlayer to be alive
                    if (!retry) {
                        // this causes a *bug* that restarts all torrents from 0
                        // but I would call this a feature
                        releasePlayer()
                        loadExo(context, listOf(), listOf(), null)
                    }
                    event(
                        StatusEvent(
                            wasPlaying = CSPlayerLoading.IsPlaying,
                            isPlaying = CSPlayerLoading.IsBuffering
                        )
                    )

                    if (Torrent.hasAcceptedTorrentForThisSession == true) {
                        loadTorrent(context, link)
                        return
                    }

                    val builder: AlertDialog.Builder = AlertDialog.Builder(context)

                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    Torrent.hasAcceptedTorrentForThisSession = true
                                    loadTorrent(context, link)
                                }

                                DialogInterface.BUTTON_NEGATIVE -> {
                                    Torrent.hasAcceptedTorrentForThisSession = false
                                    event(ErrorEvent(ErrorLoadingException("Not accepted torrent")))
                                }
                            }
                        }

                    builder.setTitle(R.string.play_torrent_button)
                        .setMessage(R.string.torrent_info)
                        // Ensure that the user will not accidentally start a torrent session.
                        .setCancelable(false).setOnCancelListener {
                            event(ErrorEvent(ErrorLoadingException("Not accepted torrent")))
                        }
                        .setPositiveButton(R.string.ok, dialogClickListener)
                        .setNegativeButton(R.string.go_back, dialogClickListener)
                        .show().setDefaultFocus()

                    return
                }
            }

            currentLink = link

            if (ignoreSSL) {
                // Disables ssl check
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), java.security.SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }


            val mediaItems = when (link) {
                is ExtractorLinkPlayList -> link.playlist.map {
                    MediaItemSlice(getMediaItem(mime, it.url), it.durationUs)
                }

                is DrmExtractorLink -> {
                    listOf(
                        // Single sliced list with unset length
                        MediaItemSlice(
                            getMediaItem(mime, link.url), Long.MIN_VALUE,
                            drm = DrmMetadata(
                                kid = link.kid,
                                key = link.key,
                                uuid = link.uuid,
                                kty = link.kty,
                                keyRequestParameters = link.keyRequestParameters
                            )
                        )
                    )
                }

                else -> listOf(
                    // Single sliced list with unset length
                    MediaItemSlice(getMediaItem(mime, link.url), Long.MIN_VALUE)
                )
            }

            val onlineSourceFactory = createOnlineSource(link)
            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                onlineSourceFactory = onlineSourceFactory,
                offlineSourceFactory = offlineSourceFactory,
                subtitleHelper
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())

            if (simpleCache == null)
                simpleCache = getCache(context, simpleCacheSize)

            val cacheFactory = CacheDataSource.Factory().apply {
                simpleCache?.let { setCache(it) }
                setUpstreamDataSourceFactory(onlineSourceFactory)
            }

            loadExo(context, mediaItems, subSources, cacheFactory)
        } catch (t: Throwable) {
            Log.e(TAG, "loadOnlinePlayer error", t)
            event(ErrorEvent(t))
        }
    }

    override fun reloadPlayer(context: Context) {
        Log.i(TAG, "reloadPlayer")

        releasePlayer(false)
        currentLink?.let {
            loadOnlinePlayer(context, it)
        } ?: currentDownloadedFile?.let {
            loadOfflinePlayer(context, it)
        }
    }
}
