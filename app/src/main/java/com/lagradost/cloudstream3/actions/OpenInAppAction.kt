package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.activityResultLauncher
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.UiText
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppContextUtils.isAppInstalled
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
fun makeTempM3U8Intent(activity: Activity,
                       intent: Intent,
                       result: LinkLoadingResult) {
    intent.apply {
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    val outputDir = activity.cacheDir

    if (result.links.size == 1) {
        intent.setDataAndType(result.links.first().url.toUri(), "video/*")
    } else {
        val outputFile = File.createTempFile("mirrorlist", ".m3u8", outputDir)

        var text = "#EXTM3U"

         //With subtitles it doesn't work for no reason :(
        /*for (sub in result.subs) {
            val normalizedName = sub.name.replace("[^a-zA-Z0-9 ]".toRegex(), "")
            val language = fromLanguageToTwoLetters(sub.name, true) ?: "und"
            text += "\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"${normalizedName}\",DEFAULT=NO,AUTOSELECT=NO,FORCED=NO,LANGUAGE=\"${language}\",URI=\"${sub.url}\""
        }*/

        for (link in result.links) {
            text += "\n#EXTINF:, ${link.name}\n${link.url}"
        }
        outputFile.writeText(text)

        intent.setDataAndType(
            FileProvider.getUriForFile(
                activity,
                activity.applicationContext.packageName + ".provider",
                outputFile
            ), "video/*"
        )
    }
}

abstract class OpenInAppAction(
    open val appName: UiText,
    open val packageName: String,
    private val intentClass: String? = null,
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
        setKey("last_opened_id", video.id)
        try {
            CoroutineScope(Dispatchers.IO).launch {
                activityResultLauncher?.launch(intent)
            }
        } catch (t: Throwable) {
            logError(t)
            main {
                if (t is ActivityNotFoundException) {
                    showToast(txt(R.string.app_not_found_error), Toast.LENGTH_LONG)
                } else {
                    showToast(t.toString(), Toast.LENGTH_LONG)
                }
            }
        }
    }

    /**
     * Before intent is sent, this function is called to put extra data into the intent.
     * @see VideoClickAction.runAction
     * */
    abstract fun putExtra(activity: Activity, intent: Intent, video: ResultEpisode, result: LinkLoadingResult, index: Int?)

    /**
     * This function is called when the app is opened again after the intent was sent.
     * You can use it to for example read duration and position.
     * @see updateDurationAndPosition
     */
    abstract fun onResult(activity: Activity, intent: Intent?)
}