package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.temp.BiglyBTPackage
import com.lagradost.cloudstream3.actions.temp.CopyClipboardAction
import com.lagradost.cloudstream3.actions.temp.JustPlayerPackage
import com.lagradost.cloudstream3.actions.temp.KodiPackage
import com.lagradost.cloudstream3.actions.temp.LibreTorrentPackage
import com.lagradost.cloudstream3.actions.temp.MpvExPackage
import com.lagradost.cloudstream3.actions.temp.MpvKtPackage
import com.lagradost.cloudstream3.actions.temp.MpvKtPreviewPackage
import com.lagradost.cloudstream3.actions.temp.MpvPackage
import com.lagradost.cloudstream3.actions.temp.MpvRxPackage
import com.lagradost.cloudstream3.actions.temp.MpvYTDLPackage
import com.lagradost.cloudstream3.actions.temp.NextPlayerPackage
import com.lagradost.cloudstream3.actions.temp.OnlyPlayer
import com.lagradost.cloudstream3.actions.temp.PlayInBrowserAction
import com.lagradost.cloudstream3.actions.temp.PlayMirrorAction
import com.lagradost.cloudstream3.actions.temp.ViewM3U8Action
import com.lagradost.cloudstream3.actions.temp.VlcNightlyPackage
import com.lagradost.cloudstream3.actions.temp.VlcPackage
import com.lagradost.cloudstream3.actions.temp.WebVideoCastPackage
import com.lagradost.cloudstream3.actions.temp.fcast.FcastAction
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.settings.SettingsFragmentScreen.launchSafe
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

object VideoClickActionHolder {
    /**
     * To avoid supporting malicious apps, we have a requirement on the app
     * being open source, popular and not brand new.
     */
    val allVideoClickActions = atomicListOf(
        // Default
        PlayInBrowserAction(),
        CopyClipboardAction(),
        ViewM3U8Action(),
        PlayMirrorAction(),
        // main support external apps
        /**
         * Source: https://github.com/videolan/vlc-android/
         * Stars: 4.1k
         * Date: Jul 2, 2012
         */
        VlcPackage(),
        /**
         * Source: https://github.com/mpv-android/mpv-android/
         * Stars: 3.7k
         * Date: Apr 20, 2016
         */
        MpvPackage(),
        /**
         * Source: https://github.com/xbmc/xbmc/
         * Stars: 21.5k
         * Date: Oct 1, 2013
         */
        KodiPackage,
        /**
         * Source: https://github.com/marlboro-advance/mpvEx/
         * Stars: 2.8k
         * Date: Nov 12, 2025
         */
        MpvExPackage(),
        /**
         * Source: https://github.com/anilbeesetti/nextplayer/
         * Stars: 4.7k
         * Date: May 19, 2023
         */
        NextPlayerPackage(),
        /**
         * Source: https://github.com/moneytoo/Player/
         * Stars: 2.8k
         * Date: Nov 16, 2020
         */
        JustPlayerPackage(),
        /**
         * Mirror: https://github.com/futo-org/fcast/
         * Source: https://gitlab.futo.org/videostreaming/fcast/
         * Stars: 700
         * Date: Sep 5, 2024
         */
        FcastAction(),
        /**
         * Source: https://github.com/proninyaroslav/libretorrent/
         * Stars: 4.1k
         * Date: Feb 5, 2021
         */
        LibreTorrentPackage(),
        /**
         * Source: https://github.com/BiglySoftware/BiglyBT-Android/
         * Stars: 500
         * Date: Nov 15, 2017
         */
        BiglyBTPackage(),
        // forks/backup apps
        /**
         * See VLC
         */
        VlcNightlyPackage(),
        /**
         * Source: Proprietary https://www.webvideocaster.com/home/
         * Reason: The built-in video caster is terrible, and no good FOSS alternatives exist.
         * If a FOSS caster is found, this should be replaced.
         */
        WebVideoCastPackage(),
        /**
         * Source: https://github.com/mpv-android/mpv-android/pull/58/
         * See MPV
         */
        MpvYTDLPackage(),
        /**
         * Source: https://github.com/abdallahmehiz/mpvKt/
         * Stars: 1.3k
         * Date: Aug 18, 2024
         */
        MpvKtPackage(),
        /**
         * See MPV KT
         */
        MpvKtPreviewPackage(),
        /**
         * Source: https://github.com/Kindness-Kismet/only_player/
         * Stars: 500
         * Date: Mar 8, 2026
         */
        OnlyPlayer(),
        /**
         * Source: https://github.com/Riteshp2001/mpvRx/
         * Stars: 900
         * Date: Apr 29, 2026
         */
        MpvRxPackage(),
        // Always Ask option
        AlwaysAskAction(),
        // added by plugins
        // ...
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

    fun getPlayers(context: Context? = null) = allVideoClickActions.filter { it.isPlayer && it.shouldShowSafe(context, null) }
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

    /** Even if VideoClickAction should not run any UI code, startActivity requires it,
     * this is a wrapper for runOnUiThread in a suspended safe context that bubble up exceptions  */
    @Throws
    suspend fun <T> uiThread(callable : Callable<T>) : T? {
        val future = FutureTask{
            try {
                Result.success(callable.call())
            } catch (t : Throwable) {
                Result.failure(t)
            }
        }
        CommonActivity.activity?.runOnUiThread(future) ?: throw ErrorLoadingException("No UI Activity, this should never happened")
        val result = withContext(Dispatchers.IO) {
            return@withContext future.get()
        }
        return result.getOrThrow()
    }

    /** Internally uses activityResultLauncher,
     * use this when the activity has a result like watched position */
    @Throws
    suspend fun launchResult(intent : Intent?, options : ActivityOptionsCompat? = null) {
        if (intent == null) {
            return
        }

        uiThread {
            MainActivity.activityResultLauncher?.launchSafe(intent, options)
        }
    }

    /** Internally uses startActivity, use this when you don't
     * have any result that needs to be stored when exiting the activity  */
    @Throws
    suspend fun launch(intent : Intent?, bundle : Bundle? = null) {
        if (intent == null) {
            return
        }

        uiThread {
            CommonActivity.activity?.startActivity(intent, bundle)
        }
    }

    fun uniqueId() = "$sourcePlugin:${this::class.qualifiedName}"

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
