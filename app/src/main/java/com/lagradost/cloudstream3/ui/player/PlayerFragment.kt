package com.lagradost.cloudstream3.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context.AUDIO_SERVICE
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
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
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.concurrent.thread


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
    private lateinit var viewModel: ResultViewModel
    private lateinit var playerData: PlayerData
    private var isLoading = true
    private lateinit var exoPlayer: SimpleExoPlayer

    private lateinit var settingsManager: SharedPreferences

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

    private var resizeMode = 0
    private var playbackSpeed = 0f
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var episodes: ArrayList<ResultEpisode> = ArrayList()

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
        exo_rew.setOnClickListener {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
           goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationRepeat(animation: Animation?) {

                }

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text.text = "$fastForwardTime"
                }
            })
            exo_rew_text.startAnimation(goLeft)
            exo_rew_text.text = "-$fastForwardTime"
            seekTime(fastForwardTime * -1000L)

        }
        exo_ffwd.setOnClickListener {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)
            seekTime(fastForwardTime * 1000L)
        }
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