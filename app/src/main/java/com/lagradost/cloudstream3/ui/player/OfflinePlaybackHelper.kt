package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import androidx.core.content.ContextCompat.getString
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.safefile.SafeFile

object OfflinePlaybackHelper {
    fun playLink(activity: Activity, url: String) {
        activity.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                LinkGenerator(
                    listOf(
                        BasicLink(url)
                    )
                )
            )
        )
    }

    fun playUri(activity: Activity, uri: Uri) {
        val name = SafeFile.fromUri(activity, uri)?.name()
        activity.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                DownloadFileGenerator(
                    listOf(
                        ExtractorUri(
                            uri = uri,
                            name = name ?: getString(activity, R.string.downloaded_file),
                            // well not the same as a normal id, but we take it as users may want to
                            // play downloaded files and save the location
                            id = kotlin.runCatching { ContentUris.parseId(uri) }.getOrNull()?.hashCode()
                        )
                    )
                )
            )
        )
    }
}