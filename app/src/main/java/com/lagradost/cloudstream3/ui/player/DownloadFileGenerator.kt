package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.SubtitleUtils.cleanDisplayName
import com.lagradost.cloudstream3.utils.SubtitleUtils.isMatchingSubtitle
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadFileInfoAndUpdateSettings
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getFolder
import kotlin.math.max
import kotlin.math.min

class DownloadFileGenerator(
    private val episodes: List<ExtractorUri>,
    private var currentIndex: Int = 0
) : IGenerator {
    override val hasCache = false
    override val canSkipLoading = false

    override fun hasNext(): Boolean {
        return currentIndex < episodes.size - 1
    }

    override fun hasPrev(): Boolean {
        return currentIndex > 0
    }

    override fun next() {
        if (hasNext())
            currentIndex++
    }

    override fun prev() {
        if (hasPrev())
            currentIndex--
    }

    override fun goto(index: Int) {
        // clamps value
        currentIndex = min(episodes.size - 1, max(0, index))
    }

    override fun getCurrentId(): Int? {
        return episodes[currentIndex].id
    }

    override fun getCurrent(offset: Int): Any? {
        return episodes.getOrNull(currentIndex + offset)
    }

    override fun getAll(): List<Any>? {
        return null
    }

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        val meta = episodes[currentIndex + offset]

        if (meta.uri == Uri.EMPTY) {
            // We do this here so that we only load it when
            // we actually need it as it can be more expensive.
            val info = meta.id?.let { id ->
                activity?.let { act ->
                    getDownloadFileInfoAndUpdateSettings(act, id)
                }
            }

            if (info != null) {
                val newMeta = meta.copy(uri = info.path)
                callback(null to newMeta)
            } else callback(null to meta)
        } else callback(null to meta)

        val ctx = context ?: return true
        val relative = meta.relativePath ?: return true
        val display = meta.displayName ?: return true

        val cleanDisplay = cleanDisplayName(display)

        getFolder(ctx, relative, meta.basePath)?.forEach { (name, uri) ->
            if (isMatchingSubtitle(name, display, cleanDisplay)) {
                val cleanName = cleanDisplayName(name)
                val realName = cleanName.removePrefix(cleanDisplay)

                subtitleCallback(
                    SubtitleData(
                        realName.ifBlank { ctx.getString(R.string.default_subtitles) },
                        uri.toString(),
                        SubtitleOrigin.DOWNLOADED_FILE,
                        name.toSubtitleMimeType(),
                        emptyMap(),
                        null
                    )
                )
            }
        }

        return true
    }
}