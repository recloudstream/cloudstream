package com.lagradost.cloudstream3.utils

import android.content.Context
import android.net.Uri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getFolder

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
                deleteSubtitleFile(context, uri)
            }
        }
    }

    private fun deleteSubtitleFile(context: Context, uri: Uri) {
        try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            if (rowsDeleted <= 0) {
                Log.e("SubtitleDeletion", "Failed to delete subtitle file: $uri")
            }
        } catch (e: Exception) {
            Log.e("SubtitleDeletion", "Error deleting subtitle file: ${e.message}")
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