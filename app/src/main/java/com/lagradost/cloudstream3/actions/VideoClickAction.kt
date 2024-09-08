package com.lagradost.cloudstream3.actions

import android.app.Activity
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType

object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>()

    private const val ACTION_ID_OFFSET = 1000

    fun makeOptionMap(activity: Activity?, video: ResultEpisode) = allVideoClickActions
        .filter { it.shouldShow(activity, video) }
        .mapIndexed { index, it -> it.name to index + ACTION_ID_OFFSET }

    fun getActionById(id: Int): VideoClickAction? = allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)
}

abstract class VideoClickAction {
    abstract val name: UiText

    /** if true, the app will show dialog to select source - result.links[index] */
    val oneSource : Boolean = false

    /** Which type of sources this action can handle. */
    val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    abstract fun shouldShow(activity: Activity?, video: ResultEpisode): Boolean

    /**
     *  This function is called when the action is clicked.
     *  @param activity The current activity
     *  @param video The episode/movie that was clicked
     *  @param result The result of the link loading, contains video & subtitle links
     *  @param index if oneSource is true, this is the index of the selected source
     */
    abstract fun runAction(activity: Activity?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)
}