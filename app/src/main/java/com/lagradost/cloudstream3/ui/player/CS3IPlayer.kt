package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.widget.FrameLayout
import androidx.media3.common.C.*
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.ExoPlayer
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
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.AppUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.EpisodeSkip
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import java.io.File
import java.lang.IllegalArgumentException
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
     * String = id
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

    @SuppressLint("UnsafeOptInUsageError")
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
            }.firstOrNull { it.first.id == id }
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

    @SuppressLint("UnsafeOptInUsageError")
    private fun Tracks.Group.getFormats(): List<Pair<Format, Int>> {
        return (0 until this.mediaTrackGroup.length).mapNotNull { i ->
            if (this.isSupported)
                this.mediaTrackGroup.getFormat(i) to i
            else null
        }
    }

    private fun Format.toAudioTrack(): AudioTrack {
        return AudioTrack(
            this.id,
            this.label,
//            isPlaying,
            this.language
        )
    }

    private fun Format.toVideoTrack(): VideoTrack {
        return VideoTrack(
            this.id,
            this.label,
//            isPlaying,
            this.language,
            this.width,
            this.height
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun getVideoTracks(): CurrentTracks {
        val allTracks = exoPlayer?.currentTracks?.groups ?: emptyList()
        val videoTracks = allTracks.filter { it.type == TRACK_TYPE_VIDEO }
            .getFormats()
            .map { it.first.toVideoTrack() }
        val audioTracks = allTracks.filter { it.type == TRACK_TYPE_AUDIO }.getFormats()
            .map { it.first.toAudioTrack() }

        return CurrentTracks(
            exoPlayer?.videoFormat?.toVideoTrack(),
            exoPlayer?.audioFormat?.toAudioTrack(),
            videoTracks,
            audioTracks
        )
    }

    /**
     * @return True if the player should be reloaded
     * */
    @SuppressLint("UnsafeOptInUsageError")
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

    var currentSubtitleOffset: Long = 0

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffset = offset
        currentTextRenderer?.setRenderOffsetMs(offset)
    }

    override fun getSubtitleOffset(): Long {
        return currentSubtitleOffset //currentTextRenderer?.getRenderOffsetMs() ?: currentSubtitleOffset
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            playerSelectedSubtitleTracks.any { (id, isSelected) ->
                isSelected && sub.getId() == id
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun getAspectRatio(): Rational? {
        return exoPlayer?.videoFormat?.let { format ->
            Rational(format.width, format.height)
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentWindowIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer(saveTime: Boolean = true) {
        Log.i(TAG, "releasePlayer")

        if (saveTime)
            updatedTime()

        exoPlayer?.apply {
            setPlayWhenReady(false)
            stop()
            release()
        }
        //simpleCache?.release()
        currentTextRenderer = null

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

        @SuppressLint("UnsafeOptInUsageError")
        private fun createOnlineSource(headers: Map<String, String>): HttpDataSource.Factory {
            val source = OkHttpDataSource.Factory(app.baseClient).setUserAgent(USER_AGENT)
            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        @SuppressLint("UnsafeOptInUsageError")
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

        @SuppressLint("UnsafeOptInUsageError")
        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSourceFactory(this, USER_AGENT)
        }

        /*private fun getSubSources(
            onlineSourceFactory: DataSource.Factory?,
            offlineSourceFactory: DataSource.Factory?,
            subHelper: PlayerSubtitleHelper,
        ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
            val activeSubtitles = ArrayList<SubtitleData>()
            val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
                val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.mimeType)
                    .setLanguage("_${sub.name}")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                when (sub.origin) {
                    SubtitleOrigin.DOWNLOADED_FILE -> {
                        if (offlineSourceFactory != null) {
                            activeSubtitles.add(sub)
                            SingleSampleMediaSource.Factory(offlineSourceFactory)
                                .createMediaSource(subConfig, C.TIME_UNSET)
                        } else {
                            null
                        }
                    }
                    SubtitleOrigin.URL -> {
                        if (onlineSourceFactory != null) {
                            activeSubtitles.add(sub)
                            SingleSampleMediaSource.Factory(onlineSourceFactory)
                                .createMediaSource(subConfig, C.TIME_UNSET)
                        } else {
                            null
                        }
                    }
                    SubtitleOrigin.OPEN_SUBTITLES -> {
                        // TODO
                        throw NotImplementedError()
                    }
                }
            }
            println("SUBSRC: ${subSources.size} activeSubtitles : ${activeSubtitles.size} of ${subHelper.getAllSubtitles().size} ")
            return Pair(subSources, activeSubtitles)
        }*/

        @SuppressLint("UnsafeOptInUsageError")
        private fun getCache(context: Context, cacheSize: Long): SimpleCache? {
            return try {
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    File(
                        context.cacheDir, "exoplayer"
                    ).also { it.deleteOnExit() }, // Ensures always fresh file
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

        @SuppressLint("UnsafeOptInUsageError")
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

        var currentTextRenderer: CustomTextRenderer? = null

        @SuppressLint("UnsafeOptInUsageError")
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
                        DefaultRenderersFactory(context).apply {
                            setEnableDecoderFallback(true)
                            // Enable Ffmpeg extension
                            setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
                        }.createRenderers(
                            eventHandler,
                            videoRendererEventListener,
                            audioRendererEventListener,
                            textRendererOutput,
                            metadataRendererOutput
                        ).map {
                            if (it is TextRenderer) {
                                val currentTextRenderer = CustomTextRenderer(
                                    subtitleOffset,
                                    textRendererOutput,
                                    eventHandler.looper,
                                    CustomSubtitleDecoderFactory()
                                ).also { this.currentTextRenderer = it }
                                currentTextRenderer
                            } else it
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

            exoPlayer?.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    normalSafeApiCall {
                        val textTracks = tracks.groups.filter { it.type == TRACK_TYPE_TEXT }

                        playerSelectedSubtitleTracks =
                            textTracks.map { group ->
                                group.getFormats().mapNotNull { (format, _) ->
                                    (format.id ?: return@mapNotNull null) to group.isSelected
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
                                        format.id!!,
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

                @SuppressLint("UnsafeOptInUsageError")
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

    @SuppressLint("UnsafeOptInUsageError")
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

    @SuppressLint("UnsafeOptInUsageError")
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

    @SuppressLint("UnsafeOptInUsageError")
    private fun getSubSources(
        onlineSourceFactory: HttpDataSource.Factory?,
        offlineSourceFactory: DataSource.Factory?,
        subHelper: PlayerSubtitleHelper,
    ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
        val activeSubtitles = ArrayList<SubtitleData>()
        val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
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

    @SuppressLint("UnsafeOptInUsageError")
    private fun loadOnlinePlayer(context: Context, link: ExtractorLink) {
        Log.i(TAG, "loadOnlinePlayer $link")
        try {
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

            val mime = when (link.type) {
                ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                ExtractorLinkType.VIDEO -> MimeTypes.VIDEO_MP4
                ExtractorLinkType.TORRENT -> throw IllegalArgumentException("No torrent support")
                ExtractorLinkType.MAGNET -> throw IllegalArgumentException("No magnet support")
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
