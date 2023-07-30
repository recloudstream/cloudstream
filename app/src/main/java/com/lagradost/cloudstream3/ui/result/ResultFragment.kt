package com.lagradost.cloudstream3.ui.result

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter.Companion.getPlayerAction
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getVideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.Event

const val START_ACTION_RESUME_LATEST = 1
const val START_ACTION_LOAD_EP = 2

/**
 * Future proofed way to mark episodes as watched
 **/
enum class VideoWatchState {
    /** Default value when no key is set */
    None,
    Watched
}

data class ResultEpisode(
    val headerName: String,
    val name: String?,
    val poster: String?,
    val episode: Int,
    val seasonIndex: Int?,  // this is the "season" index used season names
    val season: Int?, // this is the display
    val data: String,
    val apiName: String,
    val id: Int,
    val index: Int,
    val position: Long, // time in MS
    val duration: Long, // duration in MS
    val rating: Int?,
    val description: String?,
    val isFiller: Boolean?,
    val tvType: TvType,
    val parentId: Int,
    /**
     * Conveys if the episode itself is marked as watched
     **/
    val videoWatchState: VideoWatchState
)

fun ResultEpisode.getRealPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 5 || percentage >= 95) return 0
    return position
}

fun ResultEpisode.getDisplayPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 1) return 0
    if (percentage <= 5) return 5 * duration / 100
    if (percentage >= 95) return duration
    return position
}

fun buildResultEpisode(
    headerName: String,
    name: String? = null,
    poster: String? = null,
    episode: Int,
    seasonIndex: Int? = null,
    season: Int? = null,
    data: String,
    apiName: String,
    id: Int,
    index: Int,
    rating: Int? = null,
    description: String? = null,
    isFiller: Boolean? = null,
    tvType: TvType,
    parentId: Int,
): ResultEpisode {
    val posDur = getViewPos(id)
    val videoWatchState = getVideoWatchState(id) ?: VideoWatchState.None
    return ResultEpisode(
        headerName,
        name,
        poster,
        episode,
        seasonIndex,
        season,
        data,
        apiName,
        id,
        index,
        posDur?.position ?: 0,
        posDur?.duration ?: 0,
        rating,
        description,
        isFiller,
        tvType,
        parentId,
        videoWatchState
    )
}

/** 0f-1f */
fun ResultEpisode.getWatchProgress(): Float {
    return (getDisplayPosition() / 1000).toFloat() / (duration / 1000).toFloat()
}

object ResultFragment {
    private const val URL_BUNDLE = "url"
    private const val API_NAME_BUNDLE = "apiName"
    private const val SEASON_BUNDLE = "season"
    private const val EPISODE_BUNDLE = "episode"
    private const val START_ACTION_BUNDLE = "startAction"
    private const val START_VALUE_BUNDLE = "startValue"
    private const val RESTART_BUNDLE = "restart"

    fun newInstance(
        card: SearchResponse, startAction: Int = 0, startValue: Int? = null
    ): Bundle {
        return Bundle().apply {
            putString(URL_BUNDLE, card.url)
            putString(API_NAME_BUNDLE, card.apiName)
            if (card is DataStoreHelper.ResumeWatchingResult) {
                if (card.season != null)
                    putInt(SEASON_BUNDLE, card.season)
                if (card.episode != null)
                    putInt(EPISODE_BUNDLE, card.episode)
            }
            putInt(START_ACTION_BUNDLE, startAction)
            if (startValue != null)
                putInt(START_VALUE_BUNDLE, startValue)


            putBoolean(RESTART_BUNDLE, true)
        }
    }

    fun newInstance(
        url: String,
        apiName: String,
        startAction: Int = 0,
        startValue: Int = 0
    ): Bundle {
        return Bundle().apply {
            putString(URL_BUNDLE, url)
            putString(API_NAME_BUNDLE, apiName)
            putInt(START_ACTION_BUNDLE, startAction)
            putInt(START_VALUE_BUNDLE, startValue)
            putBoolean(RESTART_BUNDLE, true)
        }
    }

    fun updateUI(id: Int? = null) {
       // updateUIListener?.invoke()
        updateUIEvent.invoke(id)
    }
    val updateUIEvent = Event<Int?>()

    //private var updateUIListener: (() -> Unit)? = null


    //protected open val resultLayout = R.layout.fragment_result_swipe

    /* override var layout = R.layout.fragment_result_swipe

     override fun onCreateView(
         inflater: LayoutInflater,
         container: ViewGroup?,
         savedInstanceState: Bundle?,
     ): View? {

         return super.onCreateView(inflater, container, savedInstanceState)
         //return inflater.inflate(resultLayout, container, false)
     }

     override fun onDestroyView() {
         updateUIListener = null
         super.onDestroyView()
     }

     override fun onResume() {
         afterPluginsLoadedEvent += ::reloadViewModel
         super.onResume()
         activity?.let {
             it.window?.navigationBarColor =
                 it.colorFromAttribute(R.attr.primaryBlackBackground)
         }
     }

     override fun onDestroy() {
         afterPluginsLoadedEvent -= ::reloadViewModel
         super.onDestroy()
     }


     private fun updateUI() {
         syncModel.updateUserData()
         viewModel.reloadEpisodes()
     }*/

    data class StoredData(
        val url: String,
        val apiName: String,
        val showFillers: Boolean,
        val dubStatus: DubStatus,
        val start: AutoResume?,
        val playerAction: Int,
        val restart : Boolean,
    )

    fun Fragment.getStoredData(): StoredData? {
        val context = this.context ?: this.activity ?: return null
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val url = arguments?.getString(URL_BUNDLE) ?: return null
        val apiName = arguments?.getString(API_NAME_BUNDLE) ?: return null
        val showFillers =
            settingsManager.getBoolean(context.getString(R.string.show_fillers_key), false)
        val dubStatus = if (context.getApiDubstatusSettings()
                .contains(DubStatus.Dubbed)
        ) DubStatus.Dubbed else DubStatus.Subbed
        val startAction = arguments?.getInt(START_ACTION_BUNDLE)

        val playerAction = getPlayerAction(context)

        val restart = arguments?.getBoolean(RESTART_BUNDLE) ?: false
        if (restart) {
            arguments?.putBoolean(RESTART_BUNDLE, false)
        }

        val start = startAction?.let { action ->
            val startValue = arguments?.getInt(START_VALUE_BUNDLE)
            val resumeEpisode = arguments?.getInt(EPISODE_BUNDLE)
            val resumeSeason = arguments?.getInt(SEASON_BUNDLE)

            arguments?.remove(START_VALUE_BUNDLE)
            arguments?.remove(START_ACTION_BUNDLE)
            AutoResume(
                startAction = action,
                id = startValue,
                episode = resumeEpisode,
                season = resumeSeason
            )
        }
        return StoredData(url, apiName, showFillers, dubStatus, start, playerAction, restart)
    }

    /*private fun reloadViewModel(forceReload: Boolean) {
        if (!viewModel.hasLoaded() || forceReload) {
            val storedData = getStoredData(activity ?: context ?: return) ?: return

            viewModel.load(
                activity,
                storedData.url ?: return,
                storedData.apiName,
                storedData.showFillers,
                storedData.dubStatus,
                storedData.start
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        updateUIListener = ::updateUI

        val restart = arguments?.getBoolean(RESTART_BUNDLE) ?: false
        if (restart) {
            arguments?.putBoolean(RESTART_BUNDLE, false)
        }

        activity?.window?.decorView?.clearFocus()
        hideKeyboard()
        context?.updateHasTrailers()
        activity?.loadCache()

        //activity?.fixPaddingStatusbar(result_barstatus)

        /* val backParameter = result_back.layoutParams as FrameLayout.LayoutParams
         backParameter.setMargins(
             backParameter.leftMargin,
             backParameter.topMargin + requireContext().getStatusBarHeight(),
             backParameter.rightMargin,
             backParameter.bottomMargin
         )
         result_back.layoutParams = backParameter*/

        // activity?.fixPaddingStatusbar(result_toolbar)

        val storedData = (activity ?: context)?.let {
            getStoredData(it)
        }

        // This is to band-aid FireTV navigation
        val isTv = isTvSettings()
        result_season_button?.isFocusableInTouchMode = isTv
        result_episode_select?.isFocusableInTouchMode = isTv
        result_dub_select?.isFocusableInTouchMode = isTv
            if (storedData?.url != null) {
                if (restart || !viewModel.hasLoaded()) {
                    //viewModel.clear()
                    viewModel.load(
                        activity,
                        storedData.url,
                        storedData.apiName,
                        storedData.showFillers,
                        storedData.dubStatus,
                        storedData.start
                    )
                }
            }
    }*/
}
