package com.lagradost.cloudstream3.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context.AUDIO_SERVICE
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.animation.*
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.toPx
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.ExtractorLink
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

enum class PlayerEventType(val value: Int) {
    Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PlayPauseToggle(6)
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
)

class PlayerFragment : Fragment() {
    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private var isFullscreen = false
    private var isPlayerPlaying = true
    private var doubleTapEnabled = false
    private lateinit var viewModel: ResultViewModel
    private lateinit var playerData: PlayerData
    private var isLoading = true
    private var isShowing = true
    private lateinit var exoPlayer: SimpleExoPlayer

    private var width = Resources.getSystem().displayMetrics.heightPixels
    private var height = Resources.getSystem().displayMetrics.widthPixels

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
                        if (!ctx.isShowing) onSingleClick()
                    } else {
                        clicksLeft++
                        if (!ctx.isLocked && ctx.doubleTapEnabled) onDoubleClickLeft(clicksLeft)
                        if (!ctx.isShowing) onSingleClick()
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

                    if (ctx.isShowing && !ctx.isLocked && ctx.doubleTapEnabled) {
                        uiScope.launch {
                            delay(doubleClickQualificationSpanInMillis)
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

    private fun onClickChange() {
        isShowing = !isShowing

        click_overlay?.visibility = if (isShowing) GONE else VISIBLE

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        ObjectAnimator.ofFloat(video_title, "translationY", titleMove).apply {
            duration = 200
            start()
        }

        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        ObjectAnimator.ofFloat(bottom_player_bar, "translationY", playerBarMove).apply {
            duration = 200
            start()
        }


        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        if (!isLocked) {
            shadow_overlay?.startAnimation(fadeAnimation)
        }
        video_holder?.startAnimation(fadeAnimation)
        //video_lock_holder?.startAnimation(fadeAnimation)
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

    fun skipOP() {
        seekTime(85000L)
    }

    private var skipTime = 0L
    private var prevDiffX = 0.0
    private var preventHorizontalSwipe = false
    private var hasPassedVerticalSwipeThreshold = false
    private var hasPassedSkipLimit = false
    private val swipeEnabled = true //<settingsManager!!.getBoolean("swipe_enabled", true)
    private val swipeVerticalEnabled = true//settingsManager.getBoolean("swipe_vertical_enabled", true)
    private var isMovingStartTime = 0L
    private var currentX = 0F
    private var currentY = 0F
    private var cachedVolume = 0f

    fun handleMotionEvent(motionEvent: MotionEvent) {
        // TIME_UNSET   ==   -9223372036854775807L
        // No swiping on unloaded
        // https://exoplayer.dev/doc/reference/constant-values.html
        if (isLocked || exoPlayer.duration == -9223372036854775807L || (!swipeEnabled && !swipeVerticalEnabled)) return
        val audioManager = activity?.getSystemService(AUDIO_SERVICE) as? AudioManager

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                currentX = motionEvent.rawX
                currentY = motionEvent.rawY
                //println("DOWN: " + currentX)
                isMovingStartTime = exoPlayer.currentPosition
            }
            MotionEvent.ACTION_MOVE -> {
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

                                if (progressBarLeftHolder.alpha <= 0f) {
                                    cachedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                                }

                                progressBarLeftHolder?.alpha = 1f
                                val vol = minOf(1f,
                                    cachedVolume - diffY.toFloat() * 0.5f) // 0.05f *if (diffY > 0) 1 else -1
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
                            val alpha = minOf(0.95f,
                                brightness_overlay.alpha + diffY.toFloat() * 0.5f) // 0.05f *if (diffY > 0) 1 else -1
                            brightness_overlay?.alpha = alpha
                            //progressBarRight?.progress = ((1f - alpha) * 100).toInt()

                            progressBarRight?.max = 100 * 100
                            progressBarRight?.progress = ((1f - alpha) * 100 * 100).toInt()
                            /* val animation: ObjectAnimator = ObjectAnimator.ofInt(progressBarRight,
                                 "progress",
                                 progressBarRight.progress,
                                .toInt())
                             animation.duration = 100
                             animation.setAutoCancel(true)
                             animation.interpolator = DecelerateInterpolator()
                             animation.start()*/

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
                val transition: Transition = Fade()
                transition.duration = 1000

                TransitionManager.beginDelayedTransition(player_holder, transition)

                if (abs(skipTime) > 7000 && !preventHorizontalSwipe && swipeEnabled) {
                    exoPlayer.seekTo(maxOf(minOf(skipTime + isMovingStartTime, exoPlayer.duration), 0))
                }
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

    private fun seekTime(time: Long) {
        exoPlayer.seekTo(maxOf(minOf(exoPlayer.currentPosition + time, exoPlayer.duration), 0))
    }

    private fun releasePlayer() {
        val alphaAnimation = AlphaAnimation(0f, 1f)
        alphaAnimation.duration = 100
        alphaAnimation.fillAfter = true
        loading_overlay.startAnimation(alphaAnimation)
        video_go_back_holder.visibility = VISIBLE
        if (this::exoPlayer.isInitialized) {
            isPlayerPlaying = exoPlayer.playWhenReady
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentWindowIndex
            exoPlayer.release()
        }
    }

    private class SettingsContentObserver(handler: Handler?, val activity: Activity) : ContentObserver(handler) {
        private val audioManager = activity.getSystemService(AUDIO_SERVICE) as? AudioManager
        override fun onChange(selfChange: Boolean) {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val progressBarRight = activity.findViewById<ProgressBar>(R.id.progressBarRight)
            if (currentVolume != null && maxVolume != null) {
                progressBarRight?.progress = currentVolume * 100 / maxVolume
            }
        }
    }

    private lateinit var volumeObserver: SettingsContentObserver

    companion object {
        fun newInstance(data: PlayerData) =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("data", mapper.writeValueAsString(data))
                }
            }
    }

    private fun savePos() {
        if (this::exoPlayer.isInitialized) {
            /*if (
                    && exoPlayer.duration > 0 && exoPlayer.currentPosition > 0
            ) {
                setViewPosDur(data!!, exoPlayer.currentPosition, exoPlayer.duration)
            }*/
        }
    }

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )

    private fun updateLock() {
        video_locked_img.setImageResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        val color = if (isLocked) ContextCompat.getColor(requireContext(), R.color.videoColorPrimary)
        else Color.WHITE

        video_locked_text.setTextColor(color)
        video_locked_img.setColorFilter(color)

        val isClick = !isLocked
        println("UPDATED LOCK $isClick")
        exo_play.isClickable = isClick
        exo_pause.isClickable = isClick
        exo_ffwd.isClickable = isClick
        exo_rew.isClickable = isClick
        exo_prev.isClickable = isClick
        video_go_back.isClickable = isClick
        exo_progress.isClickable = isClick
        //next_episode_btt.isClickable = isClick
        playback_speed_btt.isClickable = isClick
        skip_op.isClickable = isClick
        resize_player.isClickable = isClick
        exo_progress.isEnabled = isClick
        //video_go_back_holder2.isEnabled = isClick

        // Clickable doesn't seem to work on com.google.android.exoplayer2.ui.DefaultTimeBar
        //exo_progress.visibility = if (isLocked) INVISIBLE else VISIBLE

        val fadeTo = if (!isLocked) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        shadow_overlay.startAnimation(fadeAnimation)
    }

    private var resizeMode = 0
    private var playbackSpeed = 0f
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var episodes: ArrayList<ResultEpisode> = ArrayList()

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        volumeObserver = SettingsContentObserver(
            Handler(
                Looper.getMainLooper()
            ), requireActivity()
        )

        activity?.contentResolver
            ?.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, volumeObserver
            )

        viewModel = ViewModelProvider(requireActivity()).get(ResultViewModel::class.java)
        arguments?.getString("data")?.let {
            playerData = mapper.readValue(it, PlayerData::class.java)
        }

        observe(viewModel.episodes) { _episodes ->
            episodes = _episodes
            if (isLoading) {
                if (playerData.episodeIndex > 0 && playerData.episodeIndex < episodes.size) {

                } else {
                    // WHAT THE FUCK DID YOU DO
                }
            }
        }

        observe(viewModel.allEpisodes) { _allEpisodes ->
            allEpisodes = _allEpisodes
        }

        observe(viewModel.resultResponse) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d is LoadResponse) {

                    }
                }
                is Resource.Failure -> {
                    //WTF, HOW DID YOU EVEN GET HERE
                }
            }
        }

        println(episodes)
        settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

        val fastForwardTime = settingsManager.getInt("fast_forward_button_time", 10)
        exo_rew_text.text = fastForwardTime.toString()
        exo_ffwd_text.text = fastForwardTime.toString()
        fun rewnd() {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text.post { exo_rew_text.text = "$fastForwardTime" }
                }
            })
            exo_rew_text.startAnimation(goLeft)
            exo_rew_text.text = "-$fastForwardTime"
            seekTime(fastForwardTime * -1000L)
        }

        exo_rew.setOnClickListener {
            rewnd()
        }

        fun ffwrd() {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    exo_ffwd_text.post { exo_ffwd_text.text = "$fastForwardTime" }
                }
            })
            exo_ffwd_text.startAnimation(goRight)
            exo_ffwd_text.text = "+$fastForwardTime"
            seekTime(fastForwardTime * 1000L)
        }

        exo_ffwd.setOnClickListener {
            ffwrd()
        }


        lock_player.setOnClickListener {
            isLocked = !isLocked
            val fadeTo = if (isLocked) 0f else 1f

            val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)
            fadeAnimation.duration = 100
            //   fadeAnimation.startOffset = 100
            fadeAnimation.fillAfter = true

            // MENUS
            centerMenu.startAnimation(fadeAnimation)
            //video_bar.startAnimation(fadeAnimation)

            // BOTTOM
            resize_player.startAnimation(fadeAnimation)
            playback_speed_btt.startAnimation(fadeAnimation)
            sources_btt.startAnimation(fadeAnimation)
            skip_op.startAnimation(fadeAnimation)
            video_go_back_holder2.startAnimation(fadeAnimation)

            updateLock()
        }

        class Listener : DoubleClickListener(this) {
            // Declaring a seekAnimation here will cause a bug

            override fun onDoubleClickRight(clicks: Int) {
                if (!isLocked) {
                    ffwrd()
                }
            }

            override fun onDoubleClickLeft(clicks: Int) {
                if (!isLocked) {
                    rewnd()
                }
            }

            override fun onSingleClick() {
                onClickChange()
                //  activity?.hideSystemUI()
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
    }

    fun getCurrentUrl(): ExtractorLink {
        return ExtractorLink("",
            "TEST",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "",
            0)
    }

    override fun onStart() {
        super.onStart()
        thread {
            // initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        thread {
            initPlayer()
            if (player_view != null) player_view.onResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // releasePlayer()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }

    override fun onPause() {
        super.onPause()
        if (player_view != null) player_view.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        if (player_view != null) player_view.onPause()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (this::exoPlayer.isInitialized) {
            outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
            outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        }
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        outState.putInt(RESIZE_MODE_KEY, resizeMode)
        outState.putFloat(PLAYBACK_SPEED, playbackSpeed)
        savePos()
        super.onSaveInstanceState(outState)
    }

    private var currentWindow = 0
    private var playbackPosition: Long = 0

    //http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
    private fun initPlayer() {
        println("INIT PLAYER")
        view?.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment
        thread {
            val currentUrl = getCurrentUrl()
            if (currentUrl == null) {
                activity?.runOnUiThread {
                    Toast.makeText(activity, "Error getting link", LENGTH_LONG).show()
                    //MainActivity.popCurrentPage()
                }
            } else {

                try {
                    activity?.runOnUiThread {
                        val isOnline =
                            currentUrl.url.startsWith("https://") || currentUrl.url.startsWith("http://")

                        if (settingsManager?.getBoolean("ignore_ssl", true) == true) {
                            // Disables ssl check
                            val sslContext: SSLContext = SSLContext.getInstance("TLS")
                            sslContext.init(null, arrayOf(SSLTrustManager()), java.security.SecureRandom())
                            sslContext.createSSLEngine()
                            HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                                true
                            }
                            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                        }

                        class CustomFactory : DataSource.Factory {
                            override fun createDataSource(): DataSource {
                                return if (isOnline) {
                                    val dataSource = DefaultHttpDataSourceFactory(USER_AGENT).createDataSource()
                                    /*FastAniApi.currentHeaders?.forEach {
                                        dataSource.setRequestProperty(it.key, it.value)
                                    }*/
                                    dataSource.setRequestProperty("Referer", currentUrl.referer)
                                    dataSource
                                } else {
                                    DefaultDataSourceFactory(requireContext(), USER_AGENT).createDataSource()
                                }
                            }
                        }

                        val mimeType = if (currentUrl.isM3u8) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4
                        val _mediaItem = MediaItem.Builder()
                            //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                            .setMimeType(mimeType)

                        if (isOnline) {
                            _mediaItem.setUri(currentUrl.url)
                        } else {
                            _mediaItem.setUri(Uri.fromFile(File(currentUrl.url)))
                        }

                        val mediaItem = _mediaItem.build()
                        val trackSelector = DefaultTrackSelector(requireContext())
                        // Disable subtitles
                        trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                            .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                            .clearSelectionOverrides()
                            .build()

                        val _exoPlayer =
                            SimpleExoPlayer.Builder(this.requireContext())
                                .setTrackSelector(trackSelector)

                        _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))
                        exoPlayer = _exoPlayer.build().apply {
                            playWhenReady = isPlayerPlaying
                            seekTo(currentWindow, playbackPosition)
                            setMediaItem(mediaItem, false)
                            prepare()
                        }

                        val alphaAnimation = AlphaAnimation(1f, 0f)
                        alphaAnimation.duration = 300
                        alphaAnimation.fillAfter = true
                        loading_overlay.startAnimation(alphaAnimation)
                        video_go_back_holder.visibility = GONE

                        exoPlayer.setHandleAudioBecomingNoisy(true) // WHEN HEADPHONES ARE PLUGGED OUT https://github.com/google/ExoPlayer/issues/7288
                        player_view.player = exoPlayer
                        // Sets the speed
                        exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed!!))
                        player_speed_text?.text = "Speed (${playbackSpeed}x)".replace(".0x", "x")

                        //https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
                        exoPlayer.addListener(object : Player.Listener {
                            //   @SuppressLint("NewApi")
                            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                                //  updatePIPModeActions()
                                if (playWhenReady && playbackState == Player.STATE_READY) {
                                    //   focusRequest?.let { activity?.requestAudioFocus(it) }
                                }
                            }

                            override fun onPlayerError(error: ExoPlaybackException) {
                                // Lets pray this doesn't spam Toasts :)
                                when (error.type) {
                                    ExoPlaybackException.TYPE_SOURCE -> {
                                        if (currentUrl.url != "") {
                                            Toast.makeText(
                                                activity,
                                                "Source error\n" + error.sourceException.message,
                                                LENGTH_LONG
                                            )
                                                .show()
                                        }
                                    }
                                    ExoPlaybackException.TYPE_REMOTE -> {
                                        Toast.makeText(activity, "Remote error", LENGTH_LONG)
                                            .show()
                                    }
                                    ExoPlaybackException.TYPE_RENDERER -> {
                                        Toast.makeText(
                                            activity,
                                            "Renderer error\n" + error.rendererException.message,
                                            LENGTH_LONG
                                        )
                                            .show()
                                    }
                                    ExoPlaybackException.TYPE_UNEXPECTED -> {
                                        Toast.makeText(
                                            activity,
                                            "Unexpected player error\n" + error.unexpectedException.message,
                                            LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        })
                    }
                } catch (e: java.lang.IllegalStateException) {
                    println("Warning: Illegal state exception in PlayerFragment")
                }
            }
        }
        //isLoadingNextEpisode = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }
}