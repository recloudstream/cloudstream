package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.getFolderPrefix
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile

object DownloadFileManagement {
    private const val RESERVED_CHARS = "|\\?*<\":>+[]/\'"
    internal fun sanitizeFilename(name: String, removeSpaces: Boolean = false): String {
        var tempName = name
        for (c in RESERVED_CHARS) {
            tempName = tempName.replace(c, ' ')
        }
        if (removeSpaces) tempName = tempName.replace(" ", "")
        return tempName.replace("  ", " ").trim(' ')
    }

    /**
     * Used for getting video player subs.
     * @return List of pairs for the files in this format: <Name, Uri>
     * */
    internal fun getFolder(
        context: Context,
        relativePath: String,
        basePath: String?
    ): List<Pair<String, Uri>>? {
        val base = basePathToFile(context, basePath)
        val folder =
            base?.gotoDirectory(relativePath, createMissingDirectories = false) ?: return null

        //if (folder.isDirectory() != false) return null

        return folder.listFiles()
            ?.mapNotNull { (it.name() ?: "") to (it.uri() ?: return@mapNotNull null) }
    }

    /**
     * Turns a string to an UniFile. Used for stored string paths such as settings.
     * Should only be used to get a download path.
     * */
    internal fun basePathToFile(context: Context, path: String?): SafeFile? {
        return when {
            path.isNullOrBlank() -> getDefaultDir(context)
            path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
            else -> SafeFile.fromFilePath(context, path)
        }
    }

    /**
     * Base path where downloaded things should be stored, changes depending on settings.
     * Returns the file and a string to be stored for future file retrieval.
     * UniFile.filePath is not sufficient for storage.
     * */
    internal fun Context.getBasePath(): Pair<SafeFile?, String?> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val basePathSetting = settingsManager.getString(getString(R.string.download_path_key), null)
        return basePathToFile(this, basePathSetting) to basePathSetting
    }

    internal fun getFileName(
        context: Context,
        metadata: DownloadObjects.DownloadEpisodeMetadata
    ): String {
        return getFileName(context, metadata.name, metadata.episode, metadata.season)
    }

    internal fun getFileName(
        context: Context,
        epName: String?,
        episode: Int?,
        season: Int?
    ): String {
        // kinda ugly ik
        return sanitizeFilename(
            if (epName == null) {
                if (season != null) {
                    "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode"
                } else {
                    "${context.getString(R.string.episode)} $episode"
                }
            } else {
                if (episode != null) {
                    if (season != null) {
                        "${context.getString(R.string.season)} $season ${context.getString(R.string.episode)} $episode - $epName"
                    } else {
                        "${context.getString(R.string.episode)} $episode - $epName"
                    }
                } else {
                    epName
                }
            }
        )
    }


    internal fun DownloadObjects.DownloadedFileInfo.toFile(context: Context): SafeFile? {
        return basePathToFile(context, this.basePath)?.gotoDirectory(
            relativePath,
            createMissingDirectories = false
        )
            ?.findFile(displayName)
    }

    internal fun getFolder(currentType: TvType, titleName: String): String {
        return if (currentType.isEpisodeBased()) {
            val sanitizedFileName = sanitizeFilename(titleName)
            "${currentType.getFolderPrefix()}/$sanitizedFileName"
        } else currentType.getFolderPrefix()
    }

    /**
     * Gets the default download path as an UniFile.
     * Vital for legacy downloads, be careful about changing anything here.
     *
     * As of writing UniFile is used for everything but download directory on scoped storage.
     * Special ContentResolver fuckery is needed for that as UniFile doesn't work.
     * */
    fun getDefaultDir(context: Context): SafeFile? {
        // See https://www.py4u.net/discuss/614761
        return SafeFile.fromMedia(
            context, MediaFileContentType.Downloads
        )
    }

}