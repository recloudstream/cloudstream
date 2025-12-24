package com.lagradost.cloudstream3.utils.downloader

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.safefile.SafeFile
import java.io.IOException
import java.io.OutputStream
import java.util.Objects

object DownloadObjects {
    /** An item can either be something to resume or something new to start */
    data class DownloadQueueWrapper(
        @JsonProperty("resumePackage") val resumePackage: DownloadResumePackage?,
        @JsonProperty("downloadItem") val downloadItem: DownloadQueueItem?,
    ) {
        init {
            assert(resumePackage != null || downloadItem != null) {
                "ResumeID and downloadItem cannot both be null at the same time!"
            }
        }

        /** Loop through the current download instances to see if it is currently downloading. Also includes link loading. */
        fun isCurrentlyDownloading(): Boolean {
            return DownloadQueueService.downloadInstances.value.any { it.downloadQueueWrapper.id == this.id }
        }

        @JsonProperty("id")
        val id = resumePackage?.item?.ep?.id ?: downloadItem!!.episode.id

        @JsonProperty("parentId")
        val parentId = resumePackage?.item?.ep?.parentId ?: downloadItem!!.episode.parentId
    }

    /** General data about the episode and show to start a download from. */
    data class DownloadQueueItem(
        @JsonProperty("episode") val episode: ResultEpisode,
        @JsonProperty("isMovie") val isMovie: Boolean,
        @JsonProperty("resultName") val resultName: String,
        @JsonProperty("resultType") val resultType: TvType,
        @JsonProperty("resultPoster") val resultPoster: String?,
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("resultId") val resultId: Int,
        @JsonProperty("resultUrl") val resultUrl: String,
        @JsonProperty("links") val links: List<ExtractorLink>? = null,
        @JsonProperty("subs") val subs: List<SubtitleData>? = null,
    ) {
        fun toWrapper(): DownloadQueueWrapper {
            return DownloadQueueWrapper(null, this)
        }
    }


    abstract class DownloadCached(
        @JsonProperty("id") open val id: Int,
    )

    data class DownloadEpisodeCached(
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("score") var score: Score? = null,
        @JsonProperty("description") val description: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        override val id: Int,
    ) : DownloadCached(id) {
        @JsonProperty("rating", access = JsonProperty.Access.WRITE_ONLY)
        @Deprecated(
            "`rating` is the old scoring system, use score instead",
            replaceWith = ReplaceWith("score"),
            level = DeprecationLevel.ERROR
        )
        var rating: Int? = null
            set(value) {
                if (value != null) {
                    @Suppress("DEPRECATION_ERROR")
                    score = Score.fromOld(value)
                }
            }
    }

    /** What to display to the user for a downloaded show/movie. Includes info such as name, poster and url */
    data class DownloadHeaderCached(
        @JsonProperty("apiName") val apiName: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("type") val type: TvType,
        @JsonProperty("name") val name: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("cacheTime") val cacheTime: Long,
        override val id: Int,
    ) : DownloadCached(id)

    data class DownloadResumePackage(
        @JsonProperty("item") val item: DownloadItem,
        /** Tills which link should get resumed */
        @JsonProperty("linkIndex") val linkIndex: Int?,
    ) {
        fun toWrapper(): DownloadQueueWrapper {
            return DownloadQueueWrapper(this, null)
        }
    }

    data class DownloadItem(
        @JsonProperty("source") val source: String?,
        @JsonProperty("folder") val folder: String?,
        @JsonProperty("ep") val ep: DownloadEpisodeMetadata,
        @JsonProperty("links") val links: List<ExtractorLink>,
    )

    /** Metadata for a specific episode and how to display it. */
    data class DownloadEpisodeMetadata(
        @JsonProperty("id") val id: Int,
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("mainName") val mainName: String,
        @JsonProperty("sourceApiName") val sourceApiName: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("type") val type: TvType?,
    )


    data class DownloadedFileInfo(
        @JsonProperty("totalBytes") val totalBytes: Long,
        @JsonProperty("relativePath") val relativePath: String,
        @JsonProperty("displayName") val displayName: String,
        @JsonProperty("extraInfo") val extraInfo: String? = null,
        @JsonProperty("basePath") val basePath: String? = null // null is for legacy downloads. See getBasePath()
    )

    data class DownloadedFileInfoResult(
        @JsonProperty("fileLength") val fileLength: Long,
        @JsonProperty("totalBytes") val totalBytes: Long,
        @JsonProperty("path") val path: Uri,
    )


    data class ResumeWatching(
        @JsonProperty("parentId") val parentId: Int,
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("updateTime") val updateTime: Long,
        @JsonProperty("isFromDownload") val isFromDownload: Boolean,
    )


    data class DownloadStatus(
        /** if you should retry with the same args and hope for a better result */
        val retrySame: Boolean,
        /** if you should try the next mirror */
        val tryNext: Boolean,
        /** if the result is what the user intended */
        val success: Boolean,
    )


    data class CreateNotificationMetadata(
        val type: VideoDownloadManager.DownloadType,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val hlsProgress: Long? = null,
        val hlsTotal: Long? = null,
        val bytesPerSecond: Long
    )

    data class StreamData(
        private val fileLength: Long,
        val file: SafeFile,
        //val fileStream: OutputStream,
    ) {
        @Throws(IOException::class)
        fun open(): OutputStream {
            return file.openOutputStreamOrThrow(resume)
        }

        @Throws(IOException::class)
        fun openNew(): OutputStream {
            return file.openOutputStreamOrThrow(false)
        }

        fun delete(): Boolean {
            return file.delete() == true
        }

        val resume: Boolean get() = fileLength > 0L
        val startAt: Long get() = if (resume) fileLength else 0L
        val exists: Boolean get() = file.exists() == true
    }


    /** bytes have the size end-start where the byte range is [start,end)
     * note that ByteArray is a pointer and therefore cant be stored without cloning it */
    data class LazyStreamDownloadResponse(
        val bytes: ByteArray,
        val startByte: Long,
        val endByte: Long,
    ) {
        val size get() = endByte - startByte

        override fun toString(): String {
            return "$startByte->$endByte"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is LazyStreamDownloadResponse) return false
            return other.startByte == startByte && other.endByte == endByte
        }

        override fun hashCode(): Int {
            return Objects.hash(startByte, endByte)
        }
    }
}