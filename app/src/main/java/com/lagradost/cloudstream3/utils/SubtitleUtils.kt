package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.VideoDownloadManager.basePathToFile

object SubtitleUtils {

    // Only these files are allowed, so no videos as subtitles
    private val allowedExtensions = listOf(
        ".vtt", ".srt", ".txt", ".ass",
        ".ttml", ".sbv", ".dfxp"
    )

    fun deleteMatchingSubtitles(context: Context, info: VideoDownloadManager.DownloadedFileInfo) {
        val cleanDisplay = cleanDisplayName(info.displayName)

        val base = basePathToFile(context, info.basePath)
        val folder =
            base?.gotoDirectory(info.relativePath, createMissingDirectories = false) ?: return
        val folderFiles = folder.listFiles() ?: return

        for (file in folderFiles) {
            val name = file.name() ?: continue
            if (!isMatchingSubtitle(name, info.displayName, cleanDisplay)) {
                continue
            }
            if (file.delete() != true) {
                Log.e("SubtitleDeletion", "Failed to delete subtitle file: $name")
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