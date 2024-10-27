package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.AppContextUtils.isAppInstalled
import com.lagradost.cloudstream3.utils.DataStoreHelper
import java.io.File

fun updateDurationAndPosition(position: Long, duration: Long) {
    if (position <= 0 || duration <= 0) return
    DataStoreHelper.setViewPos(getKey("last_opened_id"), position, duration)
    ResultFragment.updateUI()
}

/**
 * Util method that may be helpful for creating intents for apps that support m3u8 files.
 * All sources are written to a temporary m3u8 file, which is then sent to the app.
 */
fun makeTempM3U8Intent(
    context: Context,
    intent: Intent,
    result: LinkLoadingResult
) {
    if (result.links.size == 1) {
        intent.setDataAndType(result.links.first().url.toUri(), "video/*")
        return
    }

    intent.apply {
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    val outputFile = File.createTempFile("mirrorlist", ".m3u8", context.cacheDir)
    var text = "#EXTM3U\n#EXT-X-VERSION:3"

    result.links.forEach { link ->
        text += "\n#EXTINF:0,${link.name}\n${link.url}"
    }

    //With subtitles it doesn't work for no reason :(
    /*for (sub in result.subs) {
        val normalizedName = sub.name.replace("[^a-zA-Z0-9 ]".toRegex(), "")
        text += "\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${normalizedName}\",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,LANGUAGE=\"${sub.languageCode}\",URI=\"${sub.url}\""
    }*/

    text += "\n#EXT-X-ENDLIST"
    outputFile.writeText(text)

    intent.setDataAndType(
        FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            outputFile
        ), "application/x-mpegURL"
    )
}

abstract class OpenInAppAction(
    open val appName: UiText,
    open val packageName: String,
    private val intentClass: String? = null,
    private val action: String = Intent.ACTION_VIEW
) : VideoClickAction() {
    override val name: UiText
        get() = txt(R.string.episode_action_play_in_format, appName)

    override val isPlayer = true

    override fun shouldShow(context: Context?, video: ResultEpisode?) =
        context?.isAppInstalled(packageName) != false

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        if (context == null) return
        val intent = Intent(action)
        intent.setPackage(packageName)
        if (intentClass != null) {
            intent.component = ComponentName(packageName, intentClass)
        }
        putExtra(context, intent, video, result, index)
        setKey("last_opened_id", video.id)
        launchResult(intent)
    }

    /**
     * Before intent is sent, this function is called to put extra data into the intent.
     * @see VideoClickAction.runAction
     * */
    @Throws
    abstract suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    )

    /**
     * This function is called when the app is opened again after the intent was sent.
     * You can use it to for example update duration and position.
     * @see updateDurationAndPosition
     */
    @Throws
    abstract fun onResult(activity: Activity, intent: Intent?)

    /** Safe version of onResult, we don't trust extension devs to not crash the app */
    fun onResultSafe(activity: Activity, intent: Intent?) {
        try {
            onResult(activity, intent)
        } catch (t: Throwable) {
            logError(t)
        }
    }
}