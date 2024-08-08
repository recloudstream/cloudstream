package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.api.Log
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getFolder
import com.lagradost.safefile.SafeFile
import java.io.IOException

object SubtitleUtils {

    // Only these files are allowed, so no videos as subtitles
    private val allowedExtensions = listOf(
        ".vtt", ".srt", ".txt", ".ass",
        ".ttml", ".sbv", ".dfxp"
    )

    fun deleteMatchingSubtitles(context: Context, info: VideoDownloadManager.DownloadedFileInfo) {
        val relative = info.relativePath
        val display = info.displayName
        val cleanDisplay = cleanDisplayName(display)

        getFolder(context, relative, info.basePath)?.forEach { (name, uri) ->
            if (isMatchingSubtitle(name, display, cleanDisplay)) {
                val subtitleFile = SafeFile.fromUri(context, uri)
                try {
                    subtitleFile?.deleteOrThrow()
                    if (subtitleFile == null) {
                        Log.e(
                            "SubtitleDeletion",
                            "Failed to delete unknown subtitle file"
                        )
                    }
                } catch (ex: IOException) {
                    Log.e(
                        "SubtitleDeletion",
                        "Failed to delete subtitle file: ${subtitleFile?.name()}"
                    )
                    logError(ex)
                }
            }
        }
    }

    /**
     * @param name the file name of the subtitle
     * @param display the file name of the video
     * @param cleanDisplay the cleanDisplayName of the video file name
     */
    fun isMatchingSubtitle(
        name: String,
        display: String,
        cleanDisplay: String
    ): Boolean {
        // Check if the file has a valid subtitle extension
        val hasValidExtension = allowedExtensions.any { name.contains(it, ignoreCase = true) }

        // We can't have the exact same file as a subtitle
        val isNotDisplayName = !name.equals(display, ignoreCase = true)

        // Check if the file name starts with a cleaned version of the display name
        val startsWithCleanDisplay = cleanDisplayName(name).startsWith(cleanDisplay, ignoreCase = true)

        return hasValidExtension && isNotDisplayName && startsWithCleanDisplay
    }

    fun cleanDisplayName(name: String): String {
        return name.substringBeforeLast('.').trim()
    }
}