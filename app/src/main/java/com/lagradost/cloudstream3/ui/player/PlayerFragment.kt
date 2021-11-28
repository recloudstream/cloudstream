package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.*
import android.content.Context.AUDIO_SERVICE
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.metrics.PlaybackErrorEvent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.view.View.*
import android.view.WindowManager.LayoutParams.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.TIME_UNSET
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainActivity.Companion.canEnterPipMode
import com.lagradost.cloudstream3.MainActivity.Companion.getCastSession
import com.lagradost.cloudstream3.MainActivity.Companion.isInPIPMode
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.*
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.fromSaveToStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageISO639_1
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getCurrentSavedStyle
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.getFocusRequest
import com.lagradost.cloudstream3.utils.AppUtils.isCastApiAvailable
import com.lagradost.cloudstream3.utils.AppUtils.onAudioFocusEvent
import com.lagradost.cloudstream3.utils.AppUtils.requestLocalAudioFocus
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.setLastWatched
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getId
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlinx.coroutines.*
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.properties.Delegates


//http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val ACTION_MEDIA_CONTROL = "media_control"
const val EXTRA_CONTROL_TYPE = "control_type"
const val PLAYBACK_SPEED = "playback_speed"
const val RESIZE_MODE_KEY = "resize_mode" // Last used resize mode
const val PLAYBACK_SPEED_KEY = "playback_speed" // Last used playback speed

const val OPENING_PERCENTAGE = 50
const val AUTOLOAD_NEXT_EPISODE_PERCENTAGE = 80

enum class PlayerEventType(val value: Int) {
    Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PrevEpisode(5),
    PlayPauseToggle(7),
    ToggleMute(8),
    Lock(9),
    ToggleHide(10),
    ShowSpeed(11),
    ShowMirrors(12),
    Resize(13),
}

/*
data class PlayerData(
    val id: Int, // UNIQUE IDENTIFIER, USED FOR SET TIME, HASH OF slug+episodeIndex
    val titleName: String, // TITLE NAME
    val episodeName: String?, // EPISODE NAME, NULL IF MOVIE
    val episodeIndex: Int?, // EPISODE INDEX, NULL IF MOVIE
    val seasonIndex : Int?, // SEASON INDEX IF IT IS FOUND, EPISODE CAN BE GIVEN BUT SEASON IS NOT GUARANTEED
    val episodes : Int?, // MAX EPISODE
    //val seasons : Int?, // SAME AS SEASON INDEX, NOT GUARANTEED, SET TO 1
)*/
data class PlayerData(
    val episodeIndex: Int,
    val seasonIndex: Int?,
    val mirrorId: Int,
)

data class UriData(
    val uri: String,
    val basePath: String?,
    val relativePath: String,
    val displayName: String,
    val parentId: Int?,
    val id: Int?,
    val name: String,
    val episode: Int?,
    val season: Int?,
)

// YE, I KNOW, THIS COULD BE HANDLED A LOT BETTER
class PlayerFragment : Fragment() {
    // ============ TORRENT ============
    //private var torrentStream: TorrentStream? = null
    private var lastTorrentUrl = ""

    //private val isTorrent: Boolean get() = torrentStream != null
    private fun initTorrentStream(torrentUrl: String) {
        if (lastTorrentUrl == torrentUrl) return
        /*lastTorrentUrl = torrentUrl
        torrentStream?.stopStream()
        torrentStream = null

        activity?.let { act ->
            val normalPath =
                act.cacheDir.absolutePath // "${Environment.getExternalStorageDirectory()}${File.separatorChar}$relativePath"
            val torrentOptions: TorrentOptions = TorrentOptions.Builder()
                .saveLocation(normalPath)
                .removeFilesAfterStop(true)
                .build()

            torrentStream = TorrentStream.init(torrentOptions)
            torrentStream?.startStream(torrentUrl)
            torrentStream?.addListener(object : TorrentListener {
                override fun onStreamPrepared(torrent: Torrent?) {
                    showToast(activity, "Stream Prepared", LENGTH_SHORT)
                }

                override fun onStreamStarted(torrent: Torrent?) {
                    showToast(activity, "Stream Started", LENGTH_SHORT)
                }

                override fun onStreamError(torrent: Torrent?, e: java.lang.Exception?) {
                    e?.printStackTrace()
                    showToast(activity, e?.localizedMessage ?: "Error loading", LENGTH_SHORT)
                }

                override fun onStreamReady(torrent: Torrent?) {
                    initPlayer(null, null, torrent?.videoFile?.toUri())
                }

                @SuppressLint("SetTextI18n")
                override fun onStreamProgress(torrent: Torrent?, status: StreamStatus?) {
                    try {
                        println("Seeders ${status?.seeds}")
                        println("Download Speed ${status?.downloadSpeed}")
                        println("Progress ${status?.progress}%")
                        if (isShowing)
                            player_torrent_info?.visibility = VISIBLE
                        video_torrent_progress?.text =
                            "${"%.1f".format(status?.progress ?: 0f)}% at ${status?.downloadSpeed?.div(1000) ?: 0} kb/s"
                        video_torrent_seeders?.text = "${status?.seeds ?: 0} Seeders"
                        //streamSeeds.formatText(R.string.streamSeeds, status?.seeds)
                        //streamSpeed.formatText(R.string.streamDownloadSpeed, status?.downloadSpeed?.div(1024))
                        //streamProgressTxt.formatText(R.string.streamProgress, status?.progress, "%")
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                }

                override fun onStreamStopped() {
                    println("stream stopped")
                }
            })
        }*/
    }

    // =================================

    private lateinit var subStyle: SaveCaptionStyle
    private var subView: SubtitleView? = null

    private var isCurrentlyPlaying: Boolean = false
    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    lateinit var apiName: String

    private var isFullscreen = false
    private var isPlayerPlaying = true
    private val viewModel: ResultViewModel by activityViewModels()
    private lateinit var playerData: PlayerData
    private lateinit var uriData: UriData
    private var isDownloadedFile = false
    private var isShowing = true
    private lateinit var exoPlayer: SimpleExoPlayer

    //private var currentPercentage = 0
    // private var hasNextEpisode = true

    // val formatBuilder = StringBuilder()
    //  val formatter = Formatter(formatBuilder, Locale.getDefault())

    /** Cache */
    private val cacheSize = 100L * 1024L * 1024L // 100 mb
    private var simpleCache: SimpleCache? = null

    /** Layout */
    private var width = Resources.getSystem().displayMetrics.widthPixels
    private var height = Resources.getSystem().displayMetrics.heightPixels
    private var statusBarHeight by Delegates.notNull<Int>()
    private var navigationBarHeight by Delegates.notNull<Int>()


    private var isLocked = false
    private lateinit var settingsManager: SharedPreferences

    abstract class DoubleClickListener(private val ctx: PlayerFragment) : OnTouchListener {
        // The time in which the second tap should be done in order to qualify as
        // a double click

        private var doubleClickQualificationSpanInMillis: Long = 300L
        private var singleClickQualificationSpanInMillis: Long = 300L
        private var timestampLastClick: Long = 0
        private var timestampLastSingleClick: Long = 0
        private var clicksLeft = 0
        private var clicksRight = 0
        private var fingerLeftScreen = true
        abstract fun onDoubleClickRight(clicks: Int)
        abstract fun onDoubleClickLeft(clicks: Int)
        abstract fun onSingleClick()
        abstract fun onMotionEvent(event: MotionEvent)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            onMotionEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                fingerLeftScreen = true
            }
            if (event.action == MotionEvent.ACTION_DOWN) {
                fingerLeftScreen = false

                if ((SystemClock.elapsedRealtime() - timestampLastClick) < doubleClickQualificationSpanInMillis) {
                    if (event.rawX >= ctx.width / 2) {
                        clicksRight++
                        if (!ctx.isLocked && ctx.doubleTapEnabled) onDoubleClickRight(clicksRight)
                        //if (!ctx.isShowing) onSingleClick()
                    } else {
                        clicksLeft++
                        if (!ctx.isLocked && ctx.doubleTapEnabled) onDoubleClickLeft(clicksLeft)
                        //if (!ctx.isShowing) onSingleClick()
                    }
                } else if (clicksLeft == 0 && clicksRight == 0 && fingerLeftScreen) {
                    // onSingleClick()
                    // timestampLastSingleClick = SystemClock.elapsedRealtime()
                } else {
                    clicksLeft = 0
                    clicksRight = 0
                    val job = Job()
                    val uiScope = CoroutineScope(Dispatchers.Main + job)

                    fun check() {
                        if ((SystemClock.elapsedRealtime() - timestampLastSingleClick) > singleClickQualificationSpanInMillis && (SystemClock.elapsedRealtime() - timestampLastClick) > doubleClickQualificationSpanInMillis) {
                            timestampLastSingleClick = SystemClock.elapsedRealtime()
                            onSingleClick()
                        }
                    }
//ctx.isShowing &&
                    if (!ctx.isLocked && ctx.doubleTapEnabled) {
                        uiScope.launch {
                            delay(doubleClickQualificationSpanInMillis + 1)
                            check()
                        }
                    } else {
                        check()
                    }
                }
                timestampLastClick = SystemClock.elapsedRealtime()

            }

            return true
        }
    }

    private fun updateClick() {
        click_overlay?.isVisible = !isShowing

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        video_title?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        video_title_rez?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        bottom_player_bar?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
                duration = 200
                start()
            }
        }

        changeSkip()
        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        subView?.let { sView ->
            val move = if (isShowing) -((bottom_player_bar?.height?.toFloat()
                ?: 0f) + 10.toPx) else -subStyle.elevation.toPx.toFloat()
            ObjectAnimator.ofFloat(sView, "translationY", move).apply {
                duration = 200
                start()
            }
        }

        if (!isLocked) {
            player_ffwd_holder?.alpha = 1f
            player_rew_holder?.alpha = 1f
            player_pause_holder?.alpha = 1f

            shadow_overlay?.startAnimation(fadeAnimation)
            player_ffwd_holder?.startAnimation(fadeAnimation)
            player_rew_holder?.startAnimation(fadeAnimation)
            player_pause_holder?.startAnimation(fadeAnimation)
        } else {
            //player_ffwd_holder?.alpha = 0f
            //player_ffwd_holder?.alpha = 0f
            //player_pause_holder?.alpha = 0f
        }

        bottom_player_bar?.startAnimation(fadeAnimation)
        player_top_holder?.startAnimation(fadeAnimation)
        //  video_holder?.startAnimation(fadeAnimation)
        //player_torrent_info?.isVisible = (isTorrent && isShowing)
        //  player_torrent_info?.startAnimation(fadeAnimation)
        //video_lock_holder?.startAnimation(fadeAnimation)
    }

    private fun onClickChange() {
        isShowing = !isShowing
        activity?.hideSystemUI()
        updateClick()
    }

    private fun forceLetters(inp: Int, letters: Int = 2): String {
        val added: Int = letters - inp.toString().length
        return if (added > 0) {
            "0".repeat(added) + inp.toString()
        } else {
            inp.toString()
        }
    }

    private fun convertTimeToString(time: Double): String {
        val sec = time.toInt()
        val rsec = sec % 60
        val min = ceil((sec - rsec) / 60.0).toInt()
        val rmin = min % 60
        val h = ceil((min - rmin) / 60.0).toInt()
        //int rh = h;// h % 24;
        return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") + forceLetters(
            rsec
        )
    }

    private fun skipOP() {
        seekTime(85000L)
    }

    private var swipeEnabled = true //<settingsManager!!.getBoolean("swipe_enabled", true)
    private var swipeVerticalEnabled = true//settingsManager.getBoolean("swipe_vertical_enabled", true)
    private var playBackSpeedEnabled = true//settingsManager!!.getBoolean("playback_speed_enabled", false)
    private var playerResizeEnabled = true//settingsManager!!.getBoolean("player_resize_enabled", false)
    private var doubleTapEnabled = false
    private var useSystemBrightness = false
    private var useTrueSystemBrightness = false
    private val fullscreenNotch = true//settingsManager.getBoolean("fullscreen_notch", true)

    private var skipTime = 0L
    private var prevDiffX = 0.0
    private var preventHorizontalSwipe = false
    private var hasPassedVerticalSwipeThreshold = false
    private var hasPassedSkipLimit = false

    private var isMovingStartTime = 0L
    private var currentX = 0F
    private var currentY = 0F
    private var cachedVolume = 0f
    private var isValidTouch = false

    private fun getBrightness(): Float {
        return if (useSystemBrightness) {
            if (useTrueSystemBrightness) {
                1 - (Settings.System.getInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) * (1 / 255).toFloat())
            } else {
                val lp = activity?.window?.attributes
                1 - if (lp?.screenBrightness ?: -1.0f <= 0f)
                    (Settings.System.getInt(
                        context?.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS
                    ) * (1 / 255).toFloat())
                else lp?.screenBrightness!!
            }
        } else brightness_overlay.alpha
    }

    private fun setBrightness(context: Context?, alpha: Float) {
        val realAlpha = minOf(1f - 0.05f, maxOf(alpha, 0f)) // clamp
        if (useSystemBrightness) {
            if (useTrueSystemBrightness) {
                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )

                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, (realAlpha * 255).toInt()
                )
            } else {
                val lp = activity?.window?.attributes
                lp?.screenBrightness = 1 - realAlpha
                activity?.window?.attributes = lp
            }
        } else {
            brightness_overlay?.alpha = realAlpha
        }

        context?.setKey(VIDEO_PLAYER_BRIGHTNESS, realAlpha)
    }

    private fun changeBrightness(diffY: Float): Float {
        val currentBrightness = getBrightness()
        val alpha = minOf(
            maxOf(
                0.005f, // BRIGHTNESS_OVERRIDE_OFF doesn't seem to work
                currentBrightness - diffY * 0.5f
            ), 1.0f
        )
        setBrightness(context, alpha)
        return alpha
    }

    fun handleMotionEvent(motionEvent: MotionEvent) {
        // TIME_UNSET   ==   -9223372036854775807L
        // No swiping on unloaded
        // https://exoplayer.dev/doc/reference/constant-values.html
        if (isLocked || exoPlayer.duration == TIME_UNSET || (!swipeEnabled && !swipeVerticalEnabled)) return


        val audioManager = activity?.getSystemService(AUDIO_SERVICE) as? AudioManager

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                // SO YOU CAN PULL DOWN STATUSBAR OR NAVBAR
                if (motionEvent.rawY > statusBarHeight && motionEvent.rawX < width - navigationBarHeight) {
                    currentX = motionEvent.rawX
                    currentY = motionEvent.rawY
                    isValidTouch = true
                    //println("DOWN: " + currentX)
                    isMovingStartTime = exoPlayer.currentPosition
                } else {
                    isValidTouch = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isValidTouch) return
                if (swipeVerticalEnabled) {
                    val distanceMultiplierY = 2F
                    val distanceY = (motionEvent.rawY - currentY) * distanceMultiplierY
                    val diffY = distanceY * 2.0 / height

                    // Forces 'smooth' moving preventing a bug where you
                    // can make it think it moved half a screen in a frame

                    if (abs(diffY) >= 0.2 && !hasPassedSkipLimit) {
                        hasPassedVerticalSwipeThreshold = true
                        preventHorizontalSwipe = true
                    }
                    if (hasPassedVerticalSwipeThreshold) {
                        if (currentX > width * 0.5) {
                            if (audioManager != null && progressBarLeftHolder != null) {
                                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                                if (progressBarLeftHolder?.alpha ?: 0f <= 0f) {
                                    cachedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                                }

                                progressBarLeftHolder?.alpha = 1f
                                val vol = minOf(
                                    1f,
                                    cachedVolume - diffY.toFloat() * 0.5f
                                ) // 0.05f *if (diffY > 0) 1 else -1
                                cachedVolume = vol
                                //progressBarRight?.progress = ((1f - alpha) * 100).toInt()

                                progressBarLeft?.max = 100 * 100
                                progressBarLeft?.progress = ((vol) * 100 * 100).toInt()

                                if (audioManager.isVolumeFixed) {
                                    // Lmao might earrape, we'll see in bug reports
                                    exoPlayer.volume = minOf(1f, maxOf(vol, 0f))
                                } else {
                                    // audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol*, 0)
                                    val desiredVol = (vol * maxVolume).toInt()
                                    if (desiredVol != currentVolume) {
                                        val newVolumeAdjusted =
                                            if (desiredVol < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE

                                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, newVolumeAdjusted, 0)
                                    }
                                    //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                }
                                currentY = motionEvent.rawY
                            }
                        } else if (progressBarRightHolder != null) {
                            progressBarRightHolder?.alpha = 1f

                            val alpha = changeBrightness(-diffY.toFloat())
                            progressBarRight?.max = 100 * 100
                            progressBarRight?.progress = ((1f - alpha) * 100 * 100).toInt()
                            currentY = motionEvent.rawY
                        }
                    }
                }

                if (swipeEnabled) {
                    val distanceMultiplierX = 2F
                    val distanceX = (motionEvent.rawX - currentX) * distanceMultiplierX
                    val diffX = distanceX * 2.0 / width
                    if (abs(diffX - prevDiffX) > 0.5) {
                        return
                    }
                    prevDiffX = diffX

                    skipTime = ((exoPlayer.duration * (diffX * diffX) / 10) * (if (diffX < 0) -1 else 1)).toLong()
                    if (isMovingStartTime + skipTime < 0) {
                        skipTime = -isMovingStartTime
                    } else if (isMovingStartTime + skipTime > exoPlayer.duration) {
                        skipTime = exoPlayer.duration - isMovingStartTime
                    }
                    if ((abs(skipTime) > 3000 || hasPassedSkipLimit) && !preventHorizontalSwipe) {
                        hasPassedSkipLimit = true
                        val timeString =
                            "${convertTimeToString((isMovingStartTime + skipTime) / 1000.0)} [${(if (abs(skipTime) < 1000) "" else (if (skipTime > 0) "+" else "-"))}${
                                convertTimeToString(abs(skipTime / 1000.0))
                            }]"
                        timeText.alpha = 1f
                        timeText.text = timeString
                    } else {
                        timeText.alpha = 0f
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isValidTouch) return
                isValidTouch = false
                val transition: Transition = Fade()
                transition.duration = 1000

                TransitionManager.beginDelayedTransition(player_holder, transition)

                if (abs(skipTime) > 7000 && !preventHorizontalSwipe && swipeEnabled) {
                    seekTo(skipTime + isMovingStartTime)
                    //exoPlayer.seekTo(maxOf(minOf(skipTime + isMovingStartTime, exoPlayer.duration), 0))
                }
                changeSkip()

                hasPassedSkipLimit = false
                hasPassedVerticalSwipeThreshold = false
                preventHorizontalSwipe = false
                prevDiffX = 0.0
                skipTime = 0

                timeText.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarRightHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarLeftHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                //val fadeAnimation = AlphaAnimation(1f, 0f)
                //fadeAnimation.duration = 100
                //fadeAnimation.fillAfter = true
                //progressBarLeftHolder.startAnimation(fadeAnimation)
                //progressBarRightHolder.startAnimation(fadeAnimation)
                //timeText.startAnimation(fadeAnimation)

            }
        }
    }

    fun changeSkip(position: Long? = null) {
        val data = localData

        if (this::exoPlayer.isInitialized && exoPlayer.currentPosition >= 0) {
            val percentage = ((position ?: exoPlayer.currentPosition) * 100 / exoPlayer.contentDuration).toInt()
            val hasNext = hasNextEpisode()

            if (percentage >= AUTOLOAD_NEXT_EPISODE_PERCENTAGE && hasNext) {
                val ep =
                    episodes[playerData.episodeIndex + 1]

                if ((allEpisodes[ep.id]?.size ?: 0) <= 0) {
                    viewModel.loadEpisode(ep, false) {
                        //NOTHING
                    }
                }
            }
            val nextEp = percentage >= OPENING_PERCENTAGE
            val isAnime =
                data.isAnimeBased()//(data is AnimeLoadResponse && (data.type == TvType.Anime || data.type == TvType.ONA))

            skip_op?.isVisible = (isAnime && !nextEp)
            skip_episode?.isVisible = ((!isAnime || nextEp) && hasNext)
        } else {
            val isAnime = data.isAnimeBased()

            if (isAnime) {
                skip_op?.isVisible = true
                skip_episode?.isVisible = false
            } else {
                skip_episode?.isVisible = data.isEpisodeBased()
                skip_op?.isVisible = false
            }
        }
    }

    private fun seekTime(time: Long) {
        changeSkip()
        seekTo(exoPlayer.currentPosition + time)
    }

    private fun seekTo(time: Long) {
        val correctTime = maxOf(minOf(time, exoPlayer.duration), 0)
        exoPlayer.seekTo(correctTime)
        changeSkip(correctTime)
    }

    private var hasUsedFirstRender = false

    private fun savePositionInPlayer() {
        if (this::exoPlayer.isInitialized) {
            isPlayerPlaying = exoPlayer.playWhenReady
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentWindowIndex
        }
    }

    private fun safeReleasePlayer() {
        thread {
            simpleCache?.release()
        }
        if (this::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
        isCurrentlyPlaying = false
    }

    private fun releasePlayer() {
        savePos()
        val alphaAnimation = AlphaAnimation(0f, 1f)
        alphaAnimation.duration = 100
        alphaAnimation.fillAfter = true
        video_go_back_holder_holder?.visibility = VISIBLE

        overlay_loading_skip_button?.visibility = VISIBLE
        loading_overlay?.startAnimation(alphaAnimation)
        savePositionInPlayer()
        safeReleasePlayer()
    }

    private class SettingsContentObserver(handler: Handler?, val activity: Activity) : ContentObserver(handler) {
        private val audioManager = activity.getSystemService(AUDIO_SERVICE) as? AudioManager
        override fun onChange(selfChange: Boolean) {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val progressBarLeft = activity.findViewById<ProgressBar>(R.id.progressBarLeft)
            if (currentVolume != null && maxVolume != null) {
                progressBarLeft?.progress = currentVolume * 100 / maxVolume
            }
        }
    }

    companion object {
        fun String.toSubtitleMimeType(): String {
            return when {
                endsWith("vtt", true) -> MimeTypes.TEXT_VTT
                endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
                endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP // TODO get request to see
            }
        }

        fun newInstance(data: PlayerData, startPos: Long? = null): Bundle {
            return Bundle().apply {
                //println(data)
                putString("data", mapper.writeValueAsString(data))
                if (startPos != null) {
                    putLong(STATE_RESUME_POSITION, startPos)
                }
            }
        }

        fun newInstance(uriData: UriData, startPos: Long? = null): Bundle {
            return Bundle().apply {
                //println(data)
                putString("uriData", mapper.writeValueAsString(uriData))

                if (startPos != null) {
                    putLong(STATE_RESUME_POSITION, startPos)
                }
            }
        }
    }

    private fun savePos() {
        if (this::exoPlayer.isInitialized) {
            if (exoPlayer.duration > 0 && exoPlayer.currentPosition > 0) {
                context?.let { ctx ->
                    //if (this::viewModel.isInitialized) {
                    viewModel.setViewPos(
                        ctx,
                        if (isDownloadedFile) uriData.id else getEpisode()?.id,
                        exoPlayer.currentPosition,
                        exoPlayer.duration
                    )
                    /*} else {
                        ctx.setViewPos(
                            if (isDownloadedFile) uriData.id else getEpisode()?.id,
                            exoPlayer.currentPosition,
                            exoPlayer.duration
                        )
                    }*/

                    if (isDownloadedFile) {
                        ctx.setLastWatched(uriData.parentId, uriData.id, uriData.episode, uriData.season, true)
                    } else
                        viewModel.reloadEpisodes(ctx)
                }
            }
        }
    }

    private val resizeModes = listOf(
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.resize_fit),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.resize_fill),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.resize_zoom),
    )

    private var localData: LoadResponse? = null

    private fun toggleLock() {
        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        if (isLocked && isShowing) {
            player_pause_holder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        //if(isShowing) {
        val fadeTo = if (isLocked) 0f else 1f

        val fadeAnimation = AlphaAnimation(video_title.alpha, fadeTo)
        fadeAnimation.duration = 100
        //   fadeAnimation.startOffset = 100
        fadeAnimation.fillAfter = true

        // MENUS
        //centerMenu.startAnimation(fadeAnimation)
        player_pause_holder?.startAnimation(fadeAnimation)
        player_ffwd_holder?.startAnimation(fadeAnimation)
        player_rew_holder?.startAnimation(fadeAnimation)
        player_media_route_button?.startAnimation(fadeAnimation)
        //video_bar.startAnimation(fadeAnimation)

        //TITLE
        video_title_rez.startAnimation(fadeAnimation)
        video_title.startAnimation(fadeAnimation)

        // BOTTOM
        lock_holder.startAnimation(fadeAnimation)
        video_go_back_holder2.startAnimation(fadeAnimation)

        shadow_overlay.startAnimation(fadeAnimation)
        // }

        updateLock()
    }

    private fun updateLock() {
        lock_player?.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        var color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary)
        else Color.WHITE
        if (color != null) {
            lock_player?.setTextColor(color)
            lock_player?.iconTint = ColorStateList.valueOf(color)
            color = Color.argb(50, color.red, color.green, color.blue)
            lock_player?.rippleColor = ColorStateList.valueOf(color)
            //if(isLocked) {
            //    lock_player?.iconTint = ContextCompat.getColorStateList(lock_player.context, R.color.white)
//
            //} else {
            //    lock_player?.iconTint = context?.colorFromAttribute(R.attr.colorPrimary)
            //}
            //lock_player?.setColorFilter(color)
        }

        val isClick = !isLocked

        exo_play?.isClickable = isClick
        sources_btt?.isClickable = isClick
        exo_pause?.isClickable = isClick
        exo_ffwd?.isClickable = isClick
        exo_rew?.isClickable = isClick
        exo_prev?.isClickable = isClick
        video_go_back?.isClickable = isClick
        exo_progress?.isClickable = isClick
        //next_episode_btt.isClickable = isClick
        playback_speed_btt?.isClickable = isClick
        skip_op?.isClickable = isClick
        skip_episode?.isClickable = isClick
        resize_player?.isClickable = isClick
        exo_progress?.isEnabled = isClick
        player_media_route_button?.isEnabled = isClick
        if (isClick && isShowing) {
            player_pause_holder?.alpha = 1f
            player_rew_holder?.alpha = 1f
            player_ffwd_holder?.alpha = 1f
        }

        //video_go_back_holder2.isEnabled = isClick

        // Clickable doesn't seem to work on com.google.android.exoplayer2.ui.DefaultTimeBar
        //exo_progress.visibility = if (isLocked) INVISIBLE else VISIBLE


        //updateClick()
    }

    private var resizeMode = 0
    private var playbackSpeed = 0f
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var allEpisodesSubs: HashMap<Int, ArrayList<SubtitleFile>> = HashMap()
    private var episodes: List<ResultEpisode> = ArrayList()
    var currentPoster: String? = null
    var currentHeaderName: String? = null
    var currentIsMovie: Boolean? = null

    //region PIP MODE
    private fun getPen(code: PlayerEventType): PendingIntent {
        return getPen(code.value)
    }

    private fun getPen(code: Int): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                activity,
                code,
                Intent("media_control").putExtra("control_type", code),
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                activity,
                code,
                Intent("media_control").putExtra("control_type", code),
                0
            )
        }
    }

    @SuppressLint("NewApi")
    private fun getRemoteAction(id: Int, title: String, event: PlayerEventType): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(activity, id),
            title,
            title,
            getPen(event)
        )
    }

    @SuppressLint("NewApi")
    private fun updatePIPModeActions() {
        if (!isInPIPMode || !this::exoPlayer.isInitialized) return

        val actions: ArrayList<RemoteAction> = ArrayList()

        actions.add(getRemoteAction(R.drawable.go_back_30, "Go Back", PlayerEventType.SeekBack))

        if (exoPlayer.isPlaying) {
            actions.add(getRemoteAction(R.drawable.netflix_pause, "Pause", PlayerEventType.Pause))
        } else {
            actions.add(getRemoteAction(R.drawable.ic_baseline_play_arrow_24, "Play", PlayerEventType.Play))
        }

        actions.add(getRemoteAction(R.drawable.go_forward_30, "Go Forward", PlayerEventType.SeekForward))
        activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).build())
    }

    private var receiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        isInPIPMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
            player_holder.alpha = 0f
            receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    handlePlayerEvent(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                }
            }
            val filter = IntentFilter()
            filter.addAction(
                ACTION_MEDIA_CONTROL
            )
            activity?.registerReceiver(receiver, filter)
            updatePIPModeActions()
        } else {
            // Restore the full-screen UI.
            player_holder.alpha = 1f
            receiver?.let {
                activity?.unregisterReceiver(it)
            }
            activity?.hideSystemUI()
            this.view?.let { hideKeyboard(it) }
        }
    }

    private fun handlePlayerEvent(event: PlayerEventType) {
        handlePlayerEvent(event.value)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        event.keyCode.let { keyCode ->
            when (keyCode) {
                // don't allow dpad move when hidden
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP_LEFT,
                KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                    if (!isShowing) {
                        return true
                    }
                }

                // netflix capture back and hide ~monke
                KeyEvent.KEYCODE_BACK -> {
                    if (isShowing) {
                        onClickChange()
                        return true
                    }
                }
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                            if (!isShowing) {
                                onClickChange()
                                return true
                            }
                        }
                    }

                    //println("Keycode: $keyCode")
                    //showToast(
                    //    this,
                    //    "Got Keycode $keyCode | ${KeyEvent.keyCodeToString(keyCode)} \n ${event?.action}",
                    //    Toast.LENGTH_LONG
                    //)
                }
            }
        }

        return false
    }

    var lastMuteVolume = 0f

    private fun handlePlayerEvent(event: Int) {
        if (!this::exoPlayer.isInitialized) return
        try {
            when (event) {
                PlayerEventType.Play.value -> exoPlayer.play()
                PlayerEventType.Pause.value -> exoPlayer.pause()
                PlayerEventType.SeekBack.value -> seekTime(-30000L)
                PlayerEventType.SeekForward.value -> seekTime(30000L)
                PlayerEventType.PlayPauseToggle.value -> {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                }
                PlayerEventType.NextEpisode.value -> {
                    if (hasNextEpisode()) {
                        skipToNextEpisode()
                    }
                }
                PlayerEventType.PrevEpisode.value -> {
                    //TODO PrevEpisode
                }
                PlayerEventType.Lock.value -> {
                    toggleLock()
                }
                PlayerEventType.ToggleHide.value -> {
                    onClickChange()
                }
                PlayerEventType.ToggleMute.value -> {
                    if (exoPlayer.volume <= 0) {
                        //is muted
                        exoPlayer.volume = lastMuteVolume
                    } else {
                        // is not muted
                        lastMuteVolume = exoPlayer.volume
                        exoPlayer.volume = 0f
                    }
                }
                PlayerEventType.Resize.value -> {
                    resizeMode = (resizeMode + 1) % resizeModes.size

                    context?.setKey(RESIZE_MODE_KEY, resizeMode)
                    player_view?.resizeMode = resizeModes[resizeMode].first
                    showToast(activity, resizeModes[resizeMode].second, LENGTH_SHORT)
                }
                PlayerEventType.ShowSpeed.value -> {
                    val speedsText =
                        listOf("0.5x", "0.75x", "0.85x", "1x", "1.15x", "1.25x", "1.4x", "1.5x", "1.75x", "2x")
                    val speedsNumbers = listOf(0.5f, 0.75f, 0.85f, 1f, 1.15f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)
                    val speedIndex = speedsNumbers.indexOf(playbackSpeed)

                    context?.let { ctx ->
                        ctx.showDialog(speedsText, speedIndex, ctx.getString(R.string.player_speed), false, {
                            activity?.hideSystemUI()
                        }) { index ->
                            playbackSpeed = speedsNumbers[index]
                            requireContext().setKey(PLAYBACK_SPEED_KEY, playbackSpeed)
                            val param = PlaybackParameters(playbackSpeed)
                            exoPlayer.playbackParameters = param
                            playback_speed_btt?.text =
                                getString(R.string.player_speed_text_format).format(playbackSpeed).replace(".0x", "x")
                        }
                    }
                }
                PlayerEventType.ShowMirrors.value -> {
                    if (!this::exoPlayer.isInitialized) return
                    context?.let { ctx ->
                        //val isPlaying = exoPlayer.isPlaying
                        exoPlayer.pause()
                        val currentSubtitles = activeSubtitles

                        val sourceBuilder = AlertDialog.Builder(ctx, R.style.AlertDialogCustomBlack)
                            .setView(R.layout.player_select_source_and_subs)

                        val sourceDialog = sourceBuilder.create()
                        sourceDialog.show()
                        //  bottomSheetDialog.setContentView(R.layout.sort_bottom_sheet)
                        val providerList = sourceDialog.findViewById<ListView>(R.id.sort_providers)!!
                        val subtitleList = sourceDialog.findViewById<ListView>(R.id.sort_subtitles)!!
                        val applyButton = sourceDialog.findViewById<MaterialButton>(R.id.apply_btt)!!
                        val cancelButton = sourceDialog.findViewById<MaterialButton>(R.id.cancel_btt)!!
                        val subsSettings = sourceDialog.findViewById<View>(R.id.subs_settings)!!

                        subsSettings.setOnClickListener {
                            saveArguments()
                            SubtitlesFragment.push(activity)
                            sourceDialog.dismiss()
                        }
                        var sourceIndex = 0
                        var startSource = 0
                        var sources: List<ExtractorLink> = emptyList()

                        val nonSortedUrls = getUrls()
                        if (nonSortedUrls.isNullOrEmpty()) {
                            sourceDialog.findViewById<LinearLayout>(R.id.sort_sources_holder)?.visibility = GONE
                        } else {
                            sources = sortUrls(nonSortedUrls)
                            startSource = sources.indexOf(getCurrentUrl())
                            sourceIndex = startSource

                            val sourcesArrayAdapter =
                                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                            sourcesArrayAdapter.addAll(sources.map { it.name })

                            providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                            providerList.adapter = sourcesArrayAdapter
                            providerList.setSelection(sourceIndex)
                            providerList.setItemChecked(sourceIndex, true)

                            providerList.setOnItemClickListener { _, _, which, _ ->
                                sourceIndex = which
                                providerList.setItemChecked(which, true)
                            }

                            sourceDialog.setOnDismissListener {
                                activity?.hideSystemUI()
                            }
                        }

                        val startIndexFromMap =
                            currentSubtitles.map { it.removeSuffix(" ") }
                                .indexOf(preferredSubtitles.removeSuffix(" ")) + 1
                        var subtitleIndex = startIndexFromMap

                        if (currentSubtitles.isEmpty()) {
                            sourceDialog.findViewById<LinearLayout>(R.id.sort_subtitles_holder)?.visibility = GONE
                        } else {
                            val subsArrayAdapter =
                                ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                            subsArrayAdapter.add(getString(R.string.no_subtitles))
                            subsArrayAdapter.addAll(currentSubtitles)

                            subtitleList.adapter = subsArrayAdapter
                            subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                            subtitleList.setSelection(subtitleIndex)
                            subtitleList.setItemChecked(subtitleIndex, true)

                            subtitleList.setOnItemClickListener { _, _, which, _ ->
                                subtitleIndex = which
                                subtitleList.setItemChecked(which, true)
                            }
                        }

                        cancelButton.setOnClickListener {
                            sourceDialog.dismiss()
                        }

                        applyButton.setOnClickListener {
                            if (sourceIndex != startSource) {
                                playbackPosition = if (this::exoPlayer.isInitialized) exoPlayer.currentPosition else 0
                                setMirrorId(sources[sourceIndex].getId())
                                initPlayer(getCurrentUrl())
                            }
                            if (subtitleIndex != startIndexFromMap) {
                                setPreferredSubLanguage(if (subtitleIndex <= 0) null else currentSubtitles[subtitleIndex - 1])
                            }
                            sourceDialog.dismiss()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }
//endregion

    private fun onSubStyleChanged(style: SaveCaptionStyle) {
        context?.let { ctx ->
            subStyle = style
            subView?.setStyle(ctx.fromSaveToStyle(style))
            subView?.translationY = -style.elevation.toPx.toFloat()

            if (style.fixedTextSize != null) {
                subView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.fixedTextSize!!)
            } else {
                subView?.setUserDefaultTextSize()
            }
        }
    }

    private fun setPreferredSubLanguage(lang: String?) {
        //val textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT) ?: return@setOnClickListener
        val realLang = if (lang.isNullOrBlank()) "" else lang
        preferredSubtitles =
            if (realLang.length == 2) SubtitleHelper.fromTwoLettersToLanguage(realLang) ?: realLang else realLang

        if (!this::exoPlayer.isInitialized) return
        (exoPlayer.trackSelector as DefaultTrackSelector?)?.let { trackSelector ->
            if (lang.isNullOrBlank()) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setPreferredTextLanguage(realLang)
                    //.setRendererDisabled(textRendererIndex, true)
                )
            } else {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setPreferredTextLanguage(realLang)
                    //.setRendererDisabled(textRendererIndex, false)
                )
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateLock()
        context?.let { ctx ->
            setPreferredSubLanguage(ctx.getAutoSelectLanguageISO639_1())
        }

        subView = player_view?.findViewById(R.id.exo_subtitles)
        subView?.let { sView ->
            (sView.parent as ViewGroup?)?.removeView(sView)
            subtitle_holder.addView(sView)
        }

        subStyle = context?.getCurrentSavedStyle()!!
        onSubStyleChanged(subStyle)
        SubtitlesFragment.applyStyleEvent += ::onSubStyleChanged

        settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        context?.let { ctx ->
            swipeEnabled = settingsManager.getBoolean(ctx.getString(R.string.swipe_enabled_key), true)
            swipeVerticalEnabled = settingsManager.getBoolean(ctx.getString(R.string.swipe_vertical_enabled_key), true)
            playBackSpeedEnabled = settingsManager.getBoolean(ctx.getString(R.string.playback_speed_enabled_key), false)
            playerResizeEnabled = settingsManager.getBoolean(ctx.getString(R.string.player_resize_enabled_key), true)
            doubleTapEnabled = settingsManager.getBoolean(ctx.getString(R.string.double_tap_enabled_key), false)
            useSystemBrightness = settingsManager.getBoolean(ctx.getString(R.string.use_system_brightness_key), false)
        }

        if (swipeVerticalEnabled)
            setBrightness(context, context?.getKey(VIDEO_PLAYER_BRIGHTNESS) ?: 0f)

        navigationBarHeight = requireContext().getNavigationBarHeight()
        statusBarHeight = requireContext().getStatusBarHeight()

        /*player_pause_holder?.setOnClickListener {
            if (this::exoPlayer.isInitialized) {
                if (exoPlayer.isPlaying)
                    exoPlayer.pause()
                else
                    exoPlayer.play()
            }
        }*/
        activity?.let { act ->
            if (act.isCastApiAvailable() && !isDownloadedFile) {
                try {
                    CastButtonFactory.setUpMediaRouteButton(act, player_media_route_button)
                    val castContext = CastContext.getSharedInstance(requireContext())

                    if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) player_media_route_button?.visibility =
                        VISIBLE
                    castContext.addCastStateListener { state ->
                        if (player_media_route_button != null) {
                            player_media_route_button?.isVisible = state != CastState.NO_DEVICES_AVAILABLE

                            if (state == CastState.CONNECTED) {
                                if (!this::exoPlayer.isInitialized) return@addCastStateListener
                                val links = sortUrls(getUrls() ?: return@addCastStateListener)
                                val epData = getEpisode() ?: return@addCastStateListener

                                val index = links.indexOf(getCurrentUrl())
                                activity?.getCastSession()?.startCast(
                                    apiName,
                                    currentIsMovie ?: return@addCastStateListener,
                                    currentHeaderName,
                                    currentPoster,
                                    epData.index,
                                    episodes,
                                    links,
                                    context?.getSubs(supportsDownloadedFiles = false) ?: emptyList(),
                                    index,
                                    exoPlayer.currentPosition
                                )

                                /*
                                val customData =
                                    links.map { JSONObject().put("name", it.name) }
                                val jsonArray = JSONArray()
                                for (item in customData) {
                                    jsonArray.put(item)
                                }

                                val mediaItems = links.map {
                                    val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)

                                    movieMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                                        epData.name ?: "Episode ${epData.episode}")

                                    if (currentHeaderName != null)
                                        movieMetadata.putString(MediaMetadata.KEY_TITLE, currentHeaderName)

                                    val srcPoster = epData.poster ?: currentPoster
                                    if (srcPoster != null) {
                                        movieMetadata.addImage(WebImage(Uri.parse(srcPoster)))
                                    }

                                    MediaQueueItem.Builder(
                                        MediaInfo.Builder(it.url)
                                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                                            .setContentType(MimeTypes.VIDEO_UNKNOWN)

                                            .setCustomData(JSONObject().put("data", jsonArray))
                                            .setMetadata(movieMetadata)
                                            .build()
                                    )
                                        .build()
                                }.toTypedArray()

                                val castPlayer = CastPlayer(castContext)
                                castPlayer.loadItems(
                                    mediaItems,
                                    if (index > 0) index else 0,
                                    exoPlayer.currentPosition,
                                    MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                                )*/
                                //  activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
                                safeReleasePlayer()
                                activity?.popCurrentPage()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
        isDownloadedFile = false
        arguments?.getString("uriData")?.let {
            uriData = mapper.readValue(it)
            isDownloadedFile = true
        }

        arguments?.getString("data")?.let {
            playerData = mapper.readValue(it)
        }

        arguments?.getLong(STATE_RESUME_POSITION)?.let {
            playbackPosition = it
        }

        sources_btt.visibility =
            if (isDownloadedFile)
                if (context?.getSubs()?.isNullOrEmpty() != false)
                    GONE else VISIBLE
            else VISIBLE

        player_media_route_button?.isVisible = !isDownloadedFile
        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
            resizeMode = savedInstanceState.getInt(RESIZE_MODE_KEY)
            playbackSpeed = savedInstanceState.getFloat(PLAYBACK_SPEED)
        }

        resizeMode = requireContext().getKey(RESIZE_MODE_KEY, 0)!!
        playbackSpeed = requireContext().getKey(PLAYBACK_SPEED_KEY, 1f)!!

        activity?.let {
            it.contentResolver?.registerContentObserver(
                Settings.System.CONTENT_URI, true, SettingsContentObserver(
                    Handler(
                        Looper.getMainLooper()
                    ), it
                )
            )
        }

        if (!isDownloadedFile) {
            //viewModel = ViewModelProvider(activity ?: this).get(ResultViewModel::class.java)

            observeDirectly(viewModel.episodes) { _episodes ->
                episodes = _episodes
                /*if (isLoading) {
                    if (playerData.episodeIndex > 0 && playerData.episodeIndex < episodes.size) {

                    } else {
                        // WHAT THE FUCK DID YOU DO
                    }
                }*/
            }

            observe(viewModel.apiName) {
                apiName = it
            }

            overlay_loading_skip_button?.alpha = 0.5f
            observeDirectly(viewModel.allEpisodes) { _allEpisodes ->
                allEpisodes = _allEpisodes

                val current = getUrls()
                if (current != null) {
                    if (current.isNotEmpty()) {
                        overlay_loading_skip_button?.alpha = 1f
                    } else {
                        overlay_loading_skip_button?.alpha = 0.5f
                    }
                } else {
                    overlay_loading_skip_button?.alpha = 0.5f
                }
            }

            observeDirectly(viewModel.allEpisodesSubs) { _allEpisodesSubs ->
                allEpisodesSubs = _allEpisodesSubs
            }

            observeDirectly(viewModel.resultResponse) { data ->
                when (data) {
                    is Resource.Success -> {
                        val d = data.value
                        if (d is LoadResponse) {
                            localData = d
                            currentPoster = d.posterUrl
                            currentHeaderName = d.name
                            currentIsMovie = !d.isEpisodeBased()
                        }
                    }
                    is Resource.Failure -> {
                        //WTF, HOW DID YOU EVEN GET HERE
                    }
                }
            }
        }
        val fastForwardTime = settingsManager.getInt(getString(R.string.fast_forward_button_time_key), 10)
        exo_rew_text?.text = getString(R.string.rew_text_regular_format).format(fastForwardTime)
        exo_ffwd_text?.text = getString(R.string.ffw_text_regular_format).format(fastForwardTime)
        fun rewind() {
            player_rew_holder?.alpha = 1f

            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew?.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text?.post {
                        exo_rew_text?.text = getString(R.string.rew_text_regular_format).format(fastForwardTime)
                        player_rew_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_rew_text?.startAnimation(goLeft)
            exo_rew_text?.text = getString(R.string.rew_text_format).format(fastForwardTime)
            seekTime(fastForwardTime * -1000L)
        }

        exo_rew?.setOnClickListener {
            rewind()
        }

        fun fastForward() {
            player_ffwd_holder?.alpha = 1f
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd?.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_ffwd_text?.post {
                        exo_ffwd_text?.text = getString(R.string.ffw_text_regular_format).format(fastForwardTime)
                        player_ffwd_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_ffwd_text?.startAnimation(goRight)
            exo_ffwd_text?.text = getString(R.string.ffw_text_format).format(fastForwardTime)
            seekTime(fastForwardTime * 1000L)
        }

        exo_ffwd?.setOnClickListener {
            fastForward()
        }

        overlay_loading_skip_button?.setOnClickListener {
            setMirrorId(
                sortUrls(getUrls() ?: return@setOnClickListener).first()
                    .getId()
            ) // BECAUSE URLS CANT BE REORDERED
            if (!isCurrentlyPlaying) {
                initPlayer(getCurrentUrl())
            }
        }

        lock_player?.setOnClickListener {
            handlePlayerEvent(PlayerEventType.Lock)
        }

        class Listener : DoubleClickListener(this) {
            // Declaring a seekAnimation here will cause a bug

            override fun onDoubleClickRight(clicks: Int) {
                if (!isLocked) {
                    fastForward()
                } else onSingleClick()
            }

            override fun onDoubleClickLeft(clicks: Int) {
                if (!isLocked) {
                    rewind()
                } else onSingleClick()
            }

            override fun onSingleClick() {
                handlePlayerEvent(PlayerEventType.ToggleHide)
            }

            override fun onMotionEvent(event: MotionEvent) {
                handleMotionEvent(event)
            }
        }

        player_holder.setOnTouchListener(
            Listener()
        )

        click_overlay?.setOnTouchListener(
            Listener()
        )

        video_go_back.setOnClickListener {
            //activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
            activity?.popCurrentPage()
        }
        video_go_back_holder.setOnClickListener {
            //println("video_go_back_pressed")
            // activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
            activity?.popCurrentPage()
        }

        playback_speed_btt?.isVisible = playBackSpeedEnabled
        playback_speed_btt?.setOnClickListener {
            handlePlayerEvent(PlayerEventType.ShowSpeed)
        }

        sources_btt.setOnClickListener {
            handlePlayerEvent(PlayerEventType.ShowMirrors)
        }

        player_view?.resizeMode = resizeModes[resizeMode].first
        if (playerResizeEnabled) {
            resize_player?.visibility = VISIBLE
            resize_player?.setOnClickListener {
                handlePlayerEvent(PlayerEventType.Resize)
            }
        } else {
            resize_player?.visibility = GONE
        }

        skip_op?.setOnClickListener {
            skipOP()
        }

        skip_episode?.setOnClickListener {
            handlePlayerEvent(PlayerEventType.NextEpisode)
        }

        changeSkip()

        // initPlayer()
    }

    private fun getCurrentUrl(): ExtractorLink? {
        val urls = getUrls() ?: return null
        for (i in urls) {
            if (i.getId() == playerData.mirrorId) {
                return i
            }
        }

        return null
    }

    private fun getUrls(): List<ExtractorLink>? {
        return try {
            allEpisodes[getEpisode()?.id]
        } catch (e: Exception) {
            null
        }
    }

    private fun Context.getSubs(supportsDownloadedFiles: Boolean = true): List<SubtitleFile>? {
        return try {
            if (isDownloadedFile) {
                if (!supportsDownloadedFiles) return null
                val list = ArrayList<SubtitleFile>()
                VideoDownloadManager.getFolder(this, uriData.relativePath, uriData.basePath)?.forEach { file ->
                    val name = uriData.displayName.removeSuffix(".mp4")
                    if (file.first != uriData.displayName && file.first.startsWith(name)) {
                        val realName = file.first.removePrefix(name)
                            .removeSuffix(".vtt")
                            .removeSuffix(".srt")
                            .removeSuffix(".txt")
                        list.add(
                            SubtitleFile(
                                realName.ifBlank { getString(R.string.default_subtitles) },
                                file.second.toString()
                            )
                        )
                    }
                }
                return list
            } else {
                allEpisodesSubs[getEpisode()?.id]
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getEpisode(): ResultEpisode? {
        return try {
            episodes[playerData.episodeIndex]
        } catch (e: Exception) {
            null
        }
    }

    private fun hasNextEpisode(): Boolean {
        return !isDownloadedFile && episodes.size > playerData.episodeIndex + 1 // TODO FIX DOWNLOADS NEXT EPISODE
    }

    private var isCurrentlySkippingEp = false

    fun tryNextMirror() {
        val urls = getUrls()
        val current = getCurrentUrl()
        if (urls != null && current != null) {
            val id = current.getId()
            val sorted = sortUrls(urls)
            for ((i, item) in sorted.withIndex()) {
                if (item.getId() == id) {
                    if (sorted.size > i + 1) {
                        setMirrorId(sorted[i + 1].getId())
                        initPlayer()
                    }
                }
            }
        }
    }

    private fun skipToNextEpisode() {
        if (isCurrentlySkippingEp) return
        savePos()
        safeReleasePlayer()
        isCurrentlySkippingEp = true
        val copy = playerData.copy(episodeIndex = playerData.episodeIndex + 1)
        playerData = copy
        playbackPosition = 0
        initPlayer()
    }

    private fun setMirrorId(id: Int?) {
        if (id == null) return
        val copy = playerData.copy(mirrorId = id)
        playerData = copy
        //initPlayer()
    }

    override fun onStart() {
        super.onStart()
        if (!isCurrentlyPlaying) {
            initPlayer()
        }
        if (player_view != null) player_view?.onResume()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fullscreenNotch) {
            val params = activity?.window?.attributes
            params?.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            activity?.window?.attributes = params
        }

        //torrentStream?.currentTorrent?.resume()
        onAudioFocusEvent += ::handlePauseEvent

        activity?.hideSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Util.SDK_INT <= 23) {
            if (!isCurrentlyPlaying) {
                initPlayer()
            }
            if (player_view != null) player_view?.onResume()
        }
    }

    private fun handlePauseEvent(pause: Boolean) {
        if (pause) {
            handlePlayerEvent(PlayerEventType.Pause)
        }
    }

    override fun onDestroy() {
        MainActivity.playerEventListener = null
        MainActivity.keyEventListener = null
        /*  val lp = activity?.window?.attributes


          lp?.screenBrightness = 1f
          activity?.window?.attributes = lp*/
        // restoring screen brightness
        val lp = activity?.window?.attributes
        lp?.screenBrightness = BRIGHTNESS_OVERRIDE_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity?.window?.attributes = lp

        loading_overlay?.isVisible = false
        savePos()
        SubtitlesFragment.applyStyleEvent -= ::onSubStyleChanged

        // torrentStream?.stopStream()
        // torrentStream = null

        super.onDestroy()
        canEnterPipMode = false

        savePositionInPlayer()
        safeReleasePlayer()

        onAudioFocusEvent -= ::handlePauseEvent

        activity?.showSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }

    override fun onPause() {
        savePos()
        super.onPause()
        // torrentStream?.currentTorrent?.pause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) player_view?.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        savePos()
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) player_view?.onPause()
            releasePlayer()
        }
    }

    private fun saveArguments() {
        if (this::exoPlayer.isInitialized) {
            arguments?.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
            arguments?.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        }
        arguments?.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        arguments?.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        arguments?.putInt(RESIZE_MODE_KEY, resizeMode)
        arguments?.putFloat(PLAYBACK_SPEED, playbackSpeed)
        if (!isDownloadedFile && this::playerData.isInitialized) {
            arguments?.putString("data", mapper.writeValueAsString(playerData))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePos()

        if (this::exoPlayer.isInitialized) {
            outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
            outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        }
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        outState.putInt(RESIZE_MODE_KEY, resizeMode)
        outState.putFloat(PLAYBACK_SPEED, playbackSpeed)
        if (!isDownloadedFile && this::playerData.isInitialized) {
            outState.putString("data", mapper.writeValueAsString(playerData))
        }
        super.onSaveInstanceState(outState)
    }

    private var currentWindow = 0
    private var playbackPosition: Long = 0
/*
    private fun updateProgressBar() {
        val duration: Long =exoPlayer.getDuration()
        val position: Long =exoPlayer.getCurrentPosition()

        handler.removeCallbacks(updateProgressAction)
        val playbackState =  exoPlayer.getPlaybackState()
        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            var delayMs: Long
            if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                delayMs = 1000 - position % 1000
                if (delayMs < 200) {
                    delayMs += 1000
                }
            } else {
                delayMs = 1000
            }
            handler.postDelayed(updateProgressAction, delayMs)
        }
    }

    private val updateProgressAction = Runnable { updateProgressBar() }*/

    var activeSubtitles: List<String> = listOf()
    var preferredSubtitles: String = ""

    @SuppressLint("SetTextI18n")
    fun initPlayer(currentUrl: ExtractorLink?, uri: String? = null, trueUri: Uri? = null) {
        if (currentUrl == null && uri == null && trueUri == null) return
        if (currentUrl?.url?.endsWith(".torrent") == true || currentUrl?.url?.startsWith("magnet") == true) {
            initTorrentStream(currentUrl.url)//)
            return
        }
        // player_torrent_info?.visibility = if(isTorrent) VISIBLE else GONE
        //

        isShowing = true
        onClickChange()

        player_torrent_info?.isVisible = false
        //player_torrent_info?.alpha = 0f
        println("LOADED: $uri or $currentUrl")
        isCurrentlyPlaying = true
        hasUsedFirstRender = false

        try {
            if (this::exoPlayer.isInitialized) {
                savePos()
                exoPlayer.release()
            }
            val isOnline =
                currentUrl != null && (currentUrl.url.startsWith("https://") || currentUrl.url.startsWith("http://"))

            if (settingsManager.getBoolean("ignore_ssl", true) && !isDownloadedFile) {
                // Disables ssl check
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), java.security.SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }

            val mimeType =
                if (currentUrl == null && uri != null)
                    MimeTypes.APPLICATION_MP4 else
                    if (currentUrl?.isM3u8 == true)
                        MimeTypes.APPLICATION_M3U8
                    else
                        MimeTypes.APPLICATION_MP4

            val mediaItemBuilder = MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)

            if (currentUrl != null) {
                mediaItemBuilder.setUri(currentUrl.url)
            } else if (trueUri != null || uri != null) {
                val uriPrimary = trueUri ?: Uri.parse(uri)
                mediaItemBuilder.setUri(uriPrimary)
            }

            val subs = context?.getSubs() ?: emptyList()
            val subItems = ArrayList<MediaItem.Subtitle>()
            val subItemsId = ArrayList<String>()

            for (sub in sortSubs(subs)) {
                val langId = sub.lang //SubtitleHelper.fromLanguageToTwoLetters(it.lang) ?: it.lang
                subItemsId.add(langId)
                subItems.add(
                    MediaItem.Subtitle(
                        Uri.parse(sub.url),
                        sub.url.toSubtitleMimeType(),
                        langId,
                        C.SELECTION_FLAG_DEFAULT
                    )
                )
            }

            activeSubtitles = subItemsId
            mediaItemBuilder.setSubtitles(subItems)

            //might add https://github.com/ed828a/Aihua/blob/1896f46888b5a954b367e83f40b845ce174a2328/app/src/main/java/com/dew/aihua/player/playerUI/VideoPlayer.kt#L287 toggle caps

            val mediaItem = mediaItemBuilder.build()
            val trackSelector = DefaultTrackSelector(requireContext())
            // Disable subtitles
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                // .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                .clearSelectionOverrides()
                .build()

            fun getDataSourceFactory(): DataSource.Factory {
                return if (isOnline) {
                    DefaultHttpDataSource.Factory().apply {
                        setUserAgent(USER_AGENT)
                        if (currentUrl != null) {
                            val headers = mapOf(
                                "referer" to currentUrl.referer,
                                "accept" to "*/*",
                                "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                                "sec-ch-ua-mobile" to "?0",
                                "sec-fetch-user" to "?1",
                                "sec-fetch-mode" to "navigate",
                                "sec-fetch-dest" to "video"
                            ) + currentUrl.headers // Adds the headers from the provider, e.g Authorization
                            setDefaultRequestProperties(headers)
                        }

                        //https://stackoverflow.com/questions/69040127/error-code-io-bad-http-status-exoplayer-android
                        setAllowCrossProtocolRedirects(true)
                    }
                } else {
                    DefaultDataSourceFactory(requireContext(), USER_AGENT)
                }
            }

            normalSafeApiCall {
                val databaseProvider = ExoDatabaseProvider(requireContext())
                simpleCache = SimpleCache(
                    File(
                        requireContext().filesDir, "exoplayer"
                    ),
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    databaseProvider
                )
            }
            val cacheFactory = CacheDataSource.Factory().apply {
                simpleCache?.let { setCache(it) }
                setUpstreamDataSourceFactory(getDataSourceFactory())
            }

            val _exoPlayer =
                SimpleExoPlayer.Builder(requireContext())
                    .setTrackSelector(trackSelector)

            exoPlayer = _exoPlayer.build().apply {
                playWhenReady = isPlayerPlaying
                seekTo(currentWindow, playbackPosition)
                setMediaSource(
                    DefaultMediaSourceFactory(cacheFactory).createMediaSource(mediaItem),
                    playbackPosition
                )
                prepare()
            }

            val alphaAnimation = AlphaAnimation(1f, 0f)
            alphaAnimation.duration = 300
            alphaAnimation.fillAfter = true
            alphaAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    loading_overlay?.post { video_go_back_holder_holder?.visibility = GONE; }
                }
            })
            overlay_loading_skip_button?.visibility = GONE

            loading_overlay?.startAnimation(alphaAnimation)

            exoPlayer.setHandleAudioBecomingNoisy(true) // WHEN HEADPHONES ARE PLUGGED OUT https://github.com/google/ExoPlayer/issues/7288
            player_view?.player = exoPlayer
            // Sets the speed
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            playback_speed_btt?.text =
                getString(R.string.player_speed_text_format).format(playbackSpeed).replace(".0x", "x")

            var hName: String? = null
            var epEpisode: Int? = null
            var epSeason: Int? = null
            var isEpisodeBased = true

            /*if (isTorrent) {
                hName = "Torrent Stream"
                isEpisodeBased = false
            } else*/ if (isDownloadedFile) {
                hName = uriData.name
                epEpisode = uriData.episode
                epSeason = uriData.season
                isEpisodeBased = epEpisode != null
                video_title_rez?.text = ""
            } else if (localData != null && currentUrl != null) {
                val data = localData!!
                val localEpisode = getEpisode()
                if (localEpisode != null) {
                    epEpisode = localEpisode.episode
                    epSeason = localEpisode.season
                    hName = data.name
                    isEpisodeBased = data.isEpisodeBased()
                    video_title_rez?.text = currentUrl.name
                }
            }

            player_view?.performClick()

            //TODO FIX
            video_title?.text = hName +
                    if (isEpisodeBased)
                        if (epSeason == null)
                            " - ${getString(R.string.episode)} $epEpisode"
                        else
                            " \"${getString(R.string.season_short)}${epSeason}:${getString(R.string.episode_short)}${epEpisode}\""
                    else ""

/*
            exo_remaining.text = Util.getStringForTime(formatBuilder,
                formatter,
                exoPlayer.contentDuration - exoPlayer.currentPosition)

            */

            /*exoPlayer.addTextOutput { list ->
                if (list.size == 0) return@addTextOutput

                val textBuilder = StringBuilder()
                for (cue in list) {
                    textBuilder.append(cue.text).append("\n")
                }
                val subtitleText = if (textBuilder.isNotEmpty())
                    textBuilder.substring(0, textBuilder.length - 1)
                else
                    textBuilder.toString()
            }*/

            //https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
            MainActivity.playerEventListener = { eventType ->
                handlePlayerEvent(eventType)
            }

            MainActivity.keyEventListener = { keyEvent ->
                if (keyEvent != null) {
                    handleKeyEvent(keyEvent)
                } else {
                    false
                }
            }

            exoPlayer.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    isCurrentlySkippingEp = false

                    val playerHeight = exoPlayer.videoFormat?.height
                    val playerWidth = exoPlayer.videoFormat?.width

                    video_title_rez?.text =
                        if (playerHeight == null || playerWidth == null) currentUrl?.name
                            ?: "" else
                        // if (isTorrent) "${width}x${height}" else
                            if (isDownloadedFile || currentUrl?.name == null) "${playerWidth}x${playerHeight}" else "${currentUrl.name} - ${playerWidth}x${playerHeight}"

                    if (!hasUsedFirstRender) { // DON'T WANT TO SET MULTIPLE MESSAGES
                        //&& !isTorrent
                        if (!isDownloadedFile && exoPlayer.duration in 5_000..10_000) {
                            // if(getapi apiName )
                            showToast(activity, R.string.vpn_might_be_needed, LENGTH_SHORT)
                        }
                        changeSkip()
                        exoPlayer
                            .createMessage { _, _ ->
                                changeSkip()
                            }
                            .setLooper(Looper.getMainLooper())
                            .setPosition( /* positionMs= */exoPlayer.contentDuration * OPENING_PERCENTAGE / 100)
                            //   .setPayload(customPayloadData)
                            .setDeleteAfterDelivery(false)
                            .send()
                        exoPlayer
                            .createMessage { _, _ ->
                                changeSkip()
                            }
                            .setLooper(Looper.getMainLooper())
                            .setPosition( /* positionMs= */exoPlayer.contentDuration * AUTOLOAD_NEXT_EPISODE_PERCENTAGE / 100)
                            //   .setPayload(customPayloadData)
                            .setDeleteAfterDelivery(false)

                            .send()

                    } else {
                        changeSkip()
                    }
                    hasUsedFirstRender = true
                }

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    canEnterPipMode = exoPlayer.isPlaying
                    updatePIPModeActions()
                    if (activity == null) return
                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    activity?.requestLocalAudioFocus(getFocusRequest())
                                }
                            }
                            Player.STATE_ENDED -> {
                                if (hasNextEpisode()) {
                                    skipToNextEpisode()
                                }
                            }
                            Player.STATE_BUFFERING -> {
                                changeSkip()
                            }
                            else -> {
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    println("CURRENT URL: " + currentUrl?.url)
                    // Lets pray this doesn't spam Toasts :)
                    val msg = error.message ?: ""
                    val errorName = error.errorCodeName
                    when (val code = error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                            if (currentUrl?.url != "") {
                                showToast(
                                    activity,
                                    "${getString(R.string.source_error)}\n$errorName ($code)\n$msg",
                                    LENGTH_SHORT
                                )
                                tryNextMirror()
                            }
                        }
                        PlaybackException.ERROR_CODE_REMOTE_ERROR, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                            showToast(
                                activity,
                                "${getString(R.string.remote_error)}\n$errorName ($code)\n$msg",
                                LENGTH_SHORT
                            )
                        }
                        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_OTHER, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                            showToast(
                                activity,
                                "${getString(R.string.render_error)}\n$errorName ($code)\n$msg",
                                LENGTH_SHORT
                            )
                        }
                        else -> {
                            showToast(
                                activity,
                                "${getString(R.string.unexpected_error)}\n$errorName ($code)\n$msg",
                                LENGTH_SHORT
                            )
                        }
                    }

                    super.onPlayerError(error)
                }
            })
        } catch (e: java.lang.IllegalStateException) {
            println("Warning: Illegal state exception in PlayerFragment")
        } finally {
            setPreferredSubLanguage(
                if (isDownloadedFile) {
                    if (activeSubtitles.isNotEmpty()) {
                        activeSubtitles.first()
                    } else null
                } else {
                    preferredSubtitles
                }
            )
        }
    }

    private fun preferredQuality(tempCurrentUrls: List<ExtractorLink>?): Int? {
        if (tempCurrentUrls.isNullOrEmpty()) return null
        val sortedUrls = sortUrls(tempCurrentUrls).reversed()
        var currentQuality = Qualities.values().last().value
        context?.let { ctx ->
            if (this::settingsManager.isInitialized)
                currentQuality = settingsManager.getInt(ctx.getString(R.string.watch_quality_pref), currentQuality)
        }

        var currentId = sortedUrls.first().getId() // lowest quality
        for (url in sortedUrls) {
            if (url.quality > currentQuality) break
            currentId = url.getId()
        }
        return currentId
    }

    //http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
    @SuppressLint("ClickableViewAccessibility")
    private fun initPlayer() {
        if (isDownloadedFile) {
            //.removePrefix("file://").replace("%20", " ") // FIX FILE PERMISSION
            initPlayer(null, uriData.uri)
        }
        println("INIT PLAYER")
        view?.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment
        val tempCurrentUrls = getUrls()
        if (tempCurrentUrls != null) {
            setMirrorId(preferredQuality(tempCurrentUrls))
        }
        val tempUrl = getCurrentUrl()
        println("TEMP:" + tempUrl?.name)
        if (tempUrl == null) {
            val localEpisode = getEpisode()
            if (localEpisode != null) {
                viewModel.loadEpisode(localEpisode, false) {
                    //if(it is Resource.Success && it.value == true)
                    val currentUrls = getUrls()
                    if (currentUrls != null && currentUrls.isNotEmpty()) {
                        if (!isCurrentlyPlaying) {
                            setMirrorId(preferredQuality(currentUrls))
                            initPlayer(getCurrentUrl())
                        }
                    } else {
                        showToast(activity, R.string.no_links_found_toast, LENGTH_SHORT)
                    }
                }
            }
        } else {
            initPlayer(tempUrl)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }
}