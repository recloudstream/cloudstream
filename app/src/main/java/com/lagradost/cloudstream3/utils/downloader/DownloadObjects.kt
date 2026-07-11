package com.lagradost.cloudstream3.utils.downloader

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SkipSerializationTest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.services.DownloadQueueService
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.serializers.UriSerializer
import com.lagradost.cloudstream3.utils.serializers.WriteOnlySerializer
import com.lagradost.safefile.SafeFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.OutputStream
import java.util.Objects

object DownloadObjects {
    /** An item can either be something to resume or something new to start */
    @Serializable
    data class DownloadQueueWrapper(
        @JsonProperty("resumePackage") @SerialName("resumePackage") val resumePackage: DownloadResumePackage?,
        @JsonProperty("downloadItem") @SerialName("downloadItem") val downloadItem: DownloadQueueItem?,
    ) {
        init {
            assert(resumePackage != null || downloadItem != null) {
                "ResumeID and downloadItem cannot both be null at the same time!"
            }
        }

        /** Loop through the current download instances to see if it is currently downloading. Also includes link loading. */
        @JsonIgnore
        fun isCurrentlyDownloading(): Boolean {
            return DownloadQueueService.downloadInstances.value.any { it.downloadQueueWrapper.id == this.id }
        }

        @JsonProperty("id") @SerialName("id")
        val id = resumePackage?.item?.ep?.id ?: downloadItem!!.episode.id

        @JsonProperty("parentId") @SerialName("parentId")
        val parentId = resumePackage?.item?.ep?.parentId ?: downloadItem!!.episode.parentId
    }

    /** General data about the episode and show to start a download from. */
    @Serializable
    data class DownloadQueueItem(
        @JsonProperty("episode") @SerialName("episode") val episode: ResultEpisode,
        @JsonProperty("isMovie") @SerialName("isMovie") val isMovie: Boolean,
        @JsonProperty("resultName") @SerialName("resultName") val resultName: String,
        @JsonProperty("resultType") @SerialName("resultType") val resultType: TvType,
        @JsonProperty("resultPoster") @SerialName("resultPoster") val resultPoster: String?,
        @JsonProperty("apiName") @SerialName("apiName") val apiName: String,
        @JsonProperty("resultId") @SerialName("resultId") val resultId: Int,
        @JsonProperty("resultUrl") @SerialName("resultUrl") val resultUrl: String,
        @JsonProperty("links") @SerialName("links") val links: List<ExtractorLink>? = null,
        @JsonProperty("subs") @SerialName("subs") val subs: List<SubtitleData>? = null,
    ) {
        fun toWrapper(): DownloadQueueWrapper {
            return DownloadQueueWrapper(null, this)
        }
    }

    interface DownloadCached {
        val id: Int
    }

    @OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
    @KeepGeneratedSerializer
    @Serializable(with = DownloadEpisodeCached.Serializer::class)
    data class DownloadEpisodeCached(
        @JsonProperty("name") @SerialName("name") val name: String?,
        @JsonProperty("poster") @SerialName("poster") val poster: String?,
        @JsonProperty("episode") @SerialName("episode") val episode: Int,
        @JsonProperty("season") @SerialName("season") val season: Int?,
        @JsonProperty("parentId") @SerialName("parentId") val parentId: Int,
        @JsonProperty("score") @SerialName("score") var score: Score? = null,
        @JsonProperty("description") @SerialName("description") val description: String?,
        @JsonProperty("cacheTime") @SerialName("cacheTime") val cacheTime: Long,
        @JsonProperty("id") @SerialName("id") override val id: Int,
    ) : DownloadCached {
        object Serializer : WriteOnlySerializer<DownloadEpisodeCached>(
            DownloadEpisodeCached.generatedSerializer(),
            setOf("rating"),
        )

        @JsonProperty("rating", access = JsonProperty.Access.WRITE_ONLY)
        @SerialName("rating")
        @Deprecated(
            "`rating` is the old scoring system, use score instead",
            replaceWith = ReplaceWith("score"),
            level = DeprecationLevel.ERROR,
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
    @Serializable
    data class DownloadHeaderCached(
        @JsonProperty("apiName") @SerialName("apiName") val apiName: String,
        @JsonProperty("url") @SerialName("url") val url: String,
        @JsonProperty("type") @SerialName("type") val type: TvType,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("poster") @SerialName("poster") val poster: String?,
        @JsonProperty("cacheTime") @SerialName("cacheTime") val cacheTime: Long,
        @JsonProperty("id") @SerialName("id") override val id: Int,
    ) : DownloadCached

    @Serializable
    data class DownloadResumePackage(
        @JsonProperty("item") @SerialName("item") val item: DownloadItem,
        /** Tills which link should get resumed */
        @JsonProperty("linkIndex") @SerialName("linkIndex") val linkIndex: Int?,
    ) {
        fun toWrapper(): DownloadQueueWrapper {
            return DownloadQueueWrapper(this, null)
        }
    }

    @Serializable
    data class DownloadItem(
        @JsonProperty("source") @SerialName("source") val source: String?,
        @JsonProperty("folder") @SerialName("folder") val folder: String?,
        @JsonProperty("ep") @SerialName("ep") val ep: DownloadEpisodeMetadata,
        @JsonProperty("links") @SerialName("links") val links: List<ExtractorLink>,
    )

    /** Metadata for a specific episode and how to display it. */
    @Serializable
    data class DownloadEpisodeMetadata(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("parentId") @SerialName("parentId") val parentId: Int,
        @JsonProperty("mainName") @SerialName("mainName") val mainName: String,
        @JsonProperty("sourceApiName") @SerialName("sourceApiName") val sourceApiName: String?,
        @JsonProperty("poster") @SerialName("poster") val poster: String?,
        @JsonProperty("name") @SerialName("name") val name: String?,
        @JsonProperty("season") @SerialName("season") val season: Int?,
        @JsonProperty("episode") @SerialName("episode") val episode: Int?,
        @JsonProperty("type") @SerialName("type") val type: TvType?,
    )

    @Serializable
    data class DownloadedFileInfo(
        @JsonProperty("totalBytes") @SerialName("totalBytes") val totalBytes: Long,
        @JsonProperty("relativePath") @SerialName("relativePath") val relativePath: String,
        @JsonProperty("displayName") @SerialName("displayName") val displayName: String,
        @JsonProperty("extraInfo") @SerialName("extraInfo") val extraInfo: String? = null,
        @JsonProperty("basePath") @SerialName("basePath") val basePath: String? = null, // null is for legacy downloads. See getBasePath()
        // Hash of the link associated with this DownloadFile, used so not override old data in the DownloadedFileInfo
        @JsonProperty("linkHash") @SerialName("linkHash") val linkHash: Int? = null,
    )

    @Serializable
    @SkipSerializationTest // Uri has issues with Jackson
    data class DownloadedFileInfoResult(
        @JsonProperty("fileLength") @SerialName("fileLength") val fileLength: Long,
        @JsonProperty("totalBytes") @SerialName("totalBytes") val totalBytes: Long,
        @JsonProperty("path") @SerialName("path")
        @Serializable(with = UriSerializer::class)
        val path: Uri,
    )

    @Serializable
    data class ResumeWatching(
        @JsonProperty("parentId") @SerialName("parentId") val parentId: Int,
        @JsonProperty("episodeId") @SerialName("episodeId") val episodeId: Int?,
        @JsonProperty("episode") @SerialName("episode") val episode: Int?,
        @JsonProperty("season") @SerialName("season") val season: Int?,
        @JsonProperty("updateTime") @SerialName("updateTime") val updateTime: Long,
        @JsonProperty("isFromDownload") @SerialName("isFromDownload") val isFromDownload: Boolean,
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
        val bytesPerSecond: Long,
    )

    data class StreamData(
        private val fileLength: Long,
        val file: SafeFile,
        // val fileStream: OutputStream,
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

    /**
     * Bytes have the size end-start where the byte range is [start,end)
     * note that ByteArray is a pointer and therefore can't be stored
     * without cloning it.
     */
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
