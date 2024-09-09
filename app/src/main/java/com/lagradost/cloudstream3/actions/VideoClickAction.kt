package com.lagradost.cloudstream3.actions

import android.app.Activity
import com.lagradost.cloudstream3.actions.temp.MpvKtPackage
import com.lagradost.cloudstream3.actions.temp.MpvKtPreviewPackage
import com.lagradost.cloudstream3.actions.temp.MpvPackage
import com.lagradost.cloudstream3.actions.temp.MpvYTDLPackage
import com.lagradost.cloudstream3.actions.temp.PlayInBrowserAction
import com.lagradost.cloudstream3.actions.temp.TestAction
import com.lagradost.cloudstream3.actions.temp.VlcPackage
import com.lagradost.cloudstream3.actions.temp.WebVideoCastPackage
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType

object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>(
        PlayInBrowserAction(), VlcPackage(), TestAction(),
        MpvPackage(), MpvYTDLPackage(),
        WebVideoCastPackage(), MpvKtPackage(), MpvKtPreviewPackage()
    )

    private const val ACTION_ID_OFFSET = 1000

    fun makeOptionMap(activity: Activity?, video: ResultEpisode) = allVideoClickActions
        // We need to have index before filtering
        .mapIndexed { index, it -> it to index + ACTION_ID_OFFSET }
        .filter { it.first.shouldShow(activity, video) }
        .map { it.first.name to it.second }


    fun getActionById(id: Int): VideoClickAction? = allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)

    fun getByUniqueId(uniqueId: String): VideoClickAction? = allVideoClickActions.firstOrNull { it.uniqueId() == uniqueId }
}

abstract class VideoClickAction {
    abstract val name: UiText

    /** if true, the app will show dialog to select source - result.links[index] */
    open val oneSource : Boolean = false

    /** if true, this action could be selected as default one press action in settings */
    open val canBeDefault: Boolean = false

    /** Which type of sources this action can handle. */
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    fun uniqueId() = "$sourcePlugin:${this::class.simpleName}"

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