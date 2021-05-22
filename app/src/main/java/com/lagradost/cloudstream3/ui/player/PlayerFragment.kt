package com.lagradost.cloudstream3.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.io.FileDescriptor
import java.io.PrintWriter

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

    private fun seekTime(time: Long) {
        exoPlayer.seekTo(maxOf(minOf(exoPlayer.currentPosition + time, exoPlayer.duration), 0))
    }

    private fun releasePlayer() {
        if (this::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
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
        resizeMode = requireContext().getKey(RESIZE_MODE_KEY, 0)!!
        playbackSpeed = requireContext().getKey(PLAYBACK_SPEED_KEY, 1f)!!

        observe(viewModel.episodes) { _episodes ->
            episodes = _episodes
            if (isLoading) {
                if (playerData.episodeIndex > 0 && playerData.episodeIndex < episodes.size) {

                }
                else {
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

        println(allEpisodes)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(requireActivity()).get(ResultViewModel::class.java)
        arguments?.getString("data")?.let {
            playerData = mapper.readValue(it, PlayerData::class.java)
        }
        return inflater.inflate(R.layout.fragment_player, container, false)
    }
}