package com.lagradost.cloudstream3.ui.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat.getString
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.safefile.SafeFile

object OfflinePlaybackHelper {
    fun playLink(url: String) {
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

    fun playUri(context: Context, uri: Uri) {
        val name = SafeFile.fromUri(context, uri)?.name()
        activity.navigate(
            R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                DownloadFileGenerator(
                    listOf(
                        ExtractorUri(
                            uri = uri,
                            name = name ?: getString(context, R.string.downloaded_file),
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