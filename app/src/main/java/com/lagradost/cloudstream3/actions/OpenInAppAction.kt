package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import com.lagradost.cloudstream3.MainActivity.Companion.activityResultLauncher
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppContextUtils.isAppInstalled

abstract class OpenInAppAction(
    open val appName: UiText,
    open val packageName: String,
    private val intentClass: String?,
    private val action: String = Intent.ACTION_VIEW
): VideoClickAction() {
    override val name: UiText
        get() = txt(R.string.episode_action_play_in_format, appName)

    override fun shouldShow(activity: Activity?, video: ResultEpisode) = activity?.isAppInstalled(packageName) == true

    override fun runAction(
        activity: Activity?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        if (activity == null) return
        val intent = Intent(action)
        intent.setPackage(packageName)
        if (intentClass != null) {
            intent.component = ComponentName(packageName, intentClass)
        }
        putExtra(activity, intent, video, result, index)

        // TODO: understand the spaghetti that is ResultResume
        activityResultLauncher?.launch(intent)
    }

    /**
     * Before intent is sent, this function is called to put extra data into the intent.
     * @see VideoClickAction.runAction
     * */
    abstract fun putExtra(activity: Activity, intent: Intent, video: ResultEpisode, result: LinkLoadingResult, index: Int?)
}