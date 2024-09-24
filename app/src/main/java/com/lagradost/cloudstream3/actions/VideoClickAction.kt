package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.temp.CopyClipboardAction
import com.lagradost.cloudstream3.actions.temp.MpvKtPackage
import com.lagradost.cloudstream3.actions.temp.MpvKtPreviewPackage
import com.lagradost.cloudstream3.actions.temp.MpvPackage
import com.lagradost.cloudstream3.actions.temp.MpvYTDLPackage
import com.lagradost.cloudstream3.actions.temp.PlayInBrowserAction
import com.lagradost.cloudstream3.actions.temp.ViewM3U8Action
import com.lagradost.cloudstream3.actions.temp.VlcPackage
import com.lagradost.cloudstream3.actions.temp.WebVideoCastPackage
import com.lagradost.cloudstream3.actions.temp.fcast.FcastAction
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.reflect.jvm.jvmName

object VideoClickActionHolder {
    val allVideoClickActions = threadSafeListOf<VideoClickAction>(
        PlayInBrowserAction(), CopyClipboardAction(),
        VlcPackage(), ViewM3U8Action(),
        MpvPackage(), MpvYTDLPackage(),
        WebVideoCastPackage(), MpvKtPackage(), MpvKtPreviewPackage(),
        FcastAction()
    )

    init {
        Log.d("VideoClickActionHolder", "allVideoClickActions: ${allVideoClickActions.map { it.uniqueId() }}")
    }

    private const val ACTION_ID_OFFSET = 1000

    fun makeOptionMap(activity: Activity?, video: ResultEpisode) = allVideoClickActions
        // We need to have index before filtering
        .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
        .filter { it.first.shouldShowSafe(activity, video) }
        .map { it.first.name to it.second }


    fun getActionById(id: Int): VideoClickAction? = allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)

    fun getByUniqueId(uniqueId: String): VideoClickAction? = allVideoClickActions.firstOrNull { it.uniqueId() == uniqueId }

    fun uniqueIdToId(uniqueId: String?): Int? {
        if (uniqueId == null) return null
        return allVideoClickActions
            .mapIndexed { id, it -> it to id + ACTION_ID_OFFSET }
            .firstOrNull { it.first.uniqueId() == uniqueId }
            ?.second
    }

    fun getPlayers(activity: Activity? = null) = allVideoClickActions.filter { it.isPlayer && it.shouldShowSafe(activity, null) }
}

abstract class VideoClickAction {
    abstract val name: UiText

    /** if true, the app will show dialog to select source - result.links[index] */
    open val oneSource : Boolean = false

    /** if true, this action could be selected as default player (one press action) in settings */
    open val isPlayer: Boolean = false

    /** Which type of sources this action can handle. */
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    fun uniqueId() = "$sourcePlugin:${this::class.jvmName}"

    @Throws
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean

    /** Safe version of shouldShow, as we don't trust extension devs to handle exceptions,
     * however no dev *should* throw in shouldShow */
    fun shouldShowSafe(context: Context?, video: ResultEpisode?): Boolean {
        return try {
            shouldShow(context,video)
        } catch (t : Throwable) {
            logError(t)
            false
        }
    }

    /**
     *  This function is called when the action is clicked.
     *  @param context The current activity
     *  @param video The episode/movie that was clicked
     *  @param result The result of the link loading, contains video & subtitle links
     *  @param index if oneSource is true, this is the index of the selected source
     */
    @Throws
    abstract suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)

    /** Safe version of runAction, as we don't trust extension devs to handle exceptions */
    fun runActionSafe(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) = ioSafe {
        try {
            runAction(context, video, result, index)
        }  catch (_ : NotImplementedError) {
            CommonActivity.showToast("runAction has not been implemented for ${name.asStringNull(context)}, please contact the extension developer of $sourcePlugin", Toast.LENGTH_LONG)
        } catch (error : ErrorLoadingException) {
            CommonActivity.showToast(error.message, Toast.LENGTH_LONG)
        } catch (_: ActivityNotFoundException) {
            CommonActivity.showToast(R.string.app_not_found_error, Toast.LENGTH_LONG)
        } catch (t : Throwable) {
            logError(t)
            CommonActivity.showToast(t.toString(), Toast.LENGTH_LONG)
        }
    }
}