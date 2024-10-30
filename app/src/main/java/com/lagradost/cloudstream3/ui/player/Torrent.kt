package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import androidx.core.net.toUri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.fetchbutton.aria2c.Aria2Args
import com.lagradost.fetchbutton.aria2c.Aria2Settings
import com.lagradost.fetchbutton.aria2c.Aria2Starter
import com.lagradost.fetchbutton.aria2c.BtPieceSelector
import com.lagradost.fetchbutton.aria2c.DownloadListener
import com.lagradost.fetchbutton.aria2c.DownloadStatusTell
import com.lagradost.fetchbutton.aria2c.FileAllocationType
import com.lagradost.fetchbutton.aria2c.FollowMetaLinkType
import com.lagradost.fetchbutton.aria2c.Metadata
import com.lagradost.fetchbutton.aria2c.UriRequest
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

data class TorrentRequest(
    val request: ExtractorLink,
    val requestId: Long,
    val data: ExtractorUri,
)

object Torrent {
    var hasAcceptedTorrentForThisSession: Boolean? = null
    private fun getPlayableFile(
        data: Metadata,
        minimumBytes: Long
    ): Uri? {
        for (item in data.items) {
            for (file in item.files) {
                // only allow files with a length above minimumBytes
                if (file.completedLength < minimumBytes) continue
                // only allow video formats
                if (videoFormats.none { suf ->
                        file.path.contains(
                            suf,
                            ignoreCase = true
                        )
                    }) continue

                return file.path.toUri()
            }
        }
        return null
    }

    private val videoFormats = arrayOf(
        ".3g2",
        ".3gp",
        ".amv",
        ".asf",
        ".avi",
        ".drc",
        ".flv",
        ".f4v",
        ".f4p",
        ".f4a",
        ".f4b",
        ".gif",
        ".gifv",
        ".m4v",
        ".mkv",
        ".mng",
        ".mov",
        ".qt",
        ".mp4",
        ".m4p",
        ".mpg", ".mp2", ".mpeg", ".mpe", ".mpv",
        ".mpg", ".mpeg", ".m2v",
        ".MTS", ".M2TS", ".TS",
        ".mxf",
        ".nsv",
        ".ogv", ".ogg",
        //".rm", // Made for RealPlayer
        //".rmvb", // Made for RealPlayer
        ".svi",
        ".viv",
        ".vob",
        ".webm",
        ".wmv",
        ".yuv"
    )

    @Throws
    private suspend fun awaitAria2c(
        link: ExtractorLink,
        requestId: Long,
        event: ((PlayerEvent) -> Unit)?
    ): ExtractorUri {
        val minimumBytes: Long = 30 shl 20
        var hasFileChecked = false
        val defaultWait = 10
        var extraWait = defaultWait
        while (true) {
            val gid = DownloadListener.sessionIdToGid[requestId]

            // request has not yet been processed, wait for it to do
            if (gid == null) {
                delay(1000)
                continue
            }

            val metadata = DownloadListener.getInfo(gid)
            event?.invoke(
                DownloadEvent(
                    downloadedBytes = metadata.downloadedLength,
                    downloadSpeed = metadata.downloadSpeed,
                    totalBytes = metadata.totalLength,
                    connections = metadata.items.sumOf { it.connections }
                )
            )
            if(metadata.status != DownloadStatusTell.Complete) {
                extraWait = defaultWait
            }
            when (metadata.status) {
                // if completed/error/removed then we don't have to wait anymore
                DownloadStatusTell.Error,
                DownloadStatusTell.Removed -> {
                    Log.i(TAG, "awaitAria2c, Completed with status = $metadata")
                    break
                }

                // some things are downloaded in multiple parts, and is therefore important
                // to wait a bit extra
                DownloadStatusTell.Complete -> {
                    // we have waited extra, but no extra data is found
                    if (extraWait < 0) {
                        break
                    }
                    val gids = metadata.items.map { it.gid }

                    // if any follower is not found in the gids,
                    // then Complete is invalid, and we wait a bit extra
                    if (metadata.items.any { item ->
                            item.followedBy.any { follow ->
                                !gids.contains(
                                    follow
                                )
                            }
                        }) {
                        extraWait -= 1
                        delay(500)
                        continue
                    } else {
                        break
                    }
                }

                // if waiting to be added, wait more
                DownloadStatusTell.Waiting -> {
                    delay(1000)
                    continue
                }

                DownloadStatusTell.Active -> {
                    //metadata.downloadedLength >= metadata.totalLength &&
                    if (getPlayableFile(
                            metadata,
                            minimumBytes = minimumBytes
                        ) != null
                    ) {
                        Log.i(TAG, "awaitAria2c, No playable file")
                        break
                    }

                    // as we don't want to waste the users time with torrents that is useless
                    // we do this to check that at a video file exists
                    if (!hasFileChecked && metadata.totalLength > minimumBytes) {
                        hasFileChecked = true
                        if (getPlayableFile(
                                metadata,
                                minimumBytes = -1
                            ) == null
                        ) {
                            throw Exception("Download file has no video")
                        }
                    }

                    //println("downloaded ${metadata.downloadedLength}/${metadata.totalLength}")
                    delay(1000)
                    continue
                }

                // unpause any pending files
                DownloadStatusTell.Paused -> {
                    Aria2Starter.unpause(gid)
                    delay(1000)
                    continue
                }

                null -> {
                    delay(1000)
                    continue
                }
            }
        }

        val gid = DownloadListener.sessionIdToGid[requestId]
            ?: throw Exception("Unable to start download")

        val metadata = DownloadListener.getInfo(gid)

        when (metadata.status) {
            DownloadStatusTell.Active, DownloadStatusTell.Complete -> {
                val uri = getPlayableFile(metadata, minimumBytes = minimumBytes)
                    ?: throw Exception(
                        if (metadata.status == DownloadStatusTell.Active) {
                            "No playable file found, this should never happened"
                        } else {
                            "Completed, but no playable file of ${minimumBytes shr 20}MB found"
                        }
                    )
                return ExtractorUri(
                    // we require at least x MB to play the file
                    uri = uri,
                    name = link.name,
                    tvType = TvType.Torrent
                )
            }

            DownloadStatusTell.Waiting -> {
                throw Exception("Download was unable to be started")
            }

            DownloadStatusTell.Paused -> {
                throw Exception("Download is paused")
            }

            DownloadStatusTell.Error -> {
                throw Exception("Download error")
            }

            DownloadStatusTell.Removed -> {
                throw Exception("Download removed")
            }

            null -> {
                throw Exception("Unexpected download error")
            }
        }
    }

    fun release() {
        pauseAll()
        // TODO move this into Aria2Starter
        /*normalSafeApiCall { (Aria2Starter.client as? WebsocketClient)?.close() }
        normalSafeApiCall { Aria2Starter.aria2?.delete() }

        Aria2Starter.client = null
        Aria2Starter.aria2 = null*/
    }

    private fun pauseAll() {
        Aria2Starter.pauseAll()
    }

    @Throws
    private suspend fun playAria2c(
        link: ExtractorLink,
        event: ((PlayerEvent) -> Unit)?
    ): TorrentRequest {
        // ephemeral id based on url to make it unique
        val requestId = link.url.hashCode().toLong()

        val uriReq = UriRequest(
            id = requestId,
            uris = listOf(link.url),
            args = Aria2Args(
                headers = link.headers,
                referer = link.referer,
                /** torrent specifics to make it possible to stream */
                seedRatio = 0.0f,
                seedTimeMin = 0.0f,
                btPieceSelector = BtPieceSelector.Inorder,
                followTorrent = FollowMetaLinkType.Mem,
                fileAllocation = FileAllocationType.None,
                btPrioritizePiece = "head=30M,tail=30M",
                allowOverwrite = true,
                autoSaveIntervalSec = 10,
                forceSave = true,
                removeControlFile = false,
                /** Best trackers to make it faster */
                btTracker = listOf(
                    "udp://tracker.opentrackr.org:1337/announce",
                    "https://tracker2.ctix.cn/announce",
                    "https://tracker1.520.jp:443/announce",
                    "udp://opentracker.i2p.rocks:6969/announce",
                    "udp://open.tracker.cl:1337/announce",
                    "udp://open.demonii.com:1337/announce",
                    "http://tracker.openbittorrent.com:80/announce",
                    "udp://tracker.openbittorrent.com:6969/announce",
                    "udp://open.stealth.si:80/announce",
                    "udp://exodus.desync.com:6969/announce",
                    "udp://tracker-udp.gbitt.info:80/announce",
                    "udp://explodie.org:6969/announce",
                    "https://tracker.gbitt.info:443/announce",
                    "http://tracker.gbitt.info:80/announce",
                    "udp://uploads.gamecoast.net:6969/announce",
                    "udp://tracker1.bt.moack.co.kr:80/announce",
                    "udp://tracker.tiny-vps.com:6969/announce",
                    "udp://tracker.theoks.net:6969/announce",
                    "udp://tracker.dump.cl:6969/announce",
                    "udp://tracker.bittor.pw:1337/announce",
                    "https://tracker1.520.jp:443/announce",
                    "udp://opentracker.i2p.rocks:6969/announce",
                    "udp://open.tracker.cl:1337/announce",
                    "udp://open.demonii.com:1337/announce",
                    "http://tracker.openbittorrent.com:80/announce",
                    "udp://tracker.openbittorrent.com:6969/announce",
                    "udp://open.stealth.si:80/announce",
                    "udp://exodus.desync.com:6969/announce",
                    "udp://tracker-udp.gbitt.info:80/announce",
                    "udp://explodie.org:6969/announce",
                    "https://tracker.gbitt.info:443/announce",
                    "http://tracker.gbitt.info:80/announce",
                    "udp://uploads.gamecoast.net:6969/announce",
                    "udp://tracker1.bt.moack.co.kr:80/announce",
                    "udp://tracker.tiny-vps.com:6969/announce",
                    "udp://tracker.theoks.net:6969/announce",
                    "udp://tracker.dump.cl:6969/announce",
                    "udp://tracker.bittor.pw:1337/announce"
                )
            )
        )

        val metadata =
            DownloadListener.sessionIdToGid[requestId]?.let { gid -> DownloadListener.getInfo(gid) }

        when (metadata?.status) {
            DownloadStatusTell.Removed, DownloadStatusTell.Error, null -> {
                Aria2Starter.download(uriReq)
            }

            else -> Unit
        }

        try {
            return TorrentRequest(
                data = awaitAria2c(link, requestId, event),
                requestId = requestId,
                request = link
            )
        } catch (t: Throwable) {
            // if we detect any download error then we delete it as we don't want any useless background tasks
            Aria2Starter.delete(DownloadListener.sessionIdToGid[requestId], requestId)
            throw t
        }
    }

    fun deleteAllFiles(): Boolean {
        val act = CommonActivity.activity ?: return false
        val defaultDirectory = "${act.cacheDir.path}/torrent_tmp"
        return File(defaultDirectory).deleteRecursively()
    }

    fun deleteAllOldFiles(): Boolean {
        try {
            val act = CommonActivity.activity ?: return false
            val defaultDirectory = "${act.cacheDir.path}/torrent_tmp"
            return File(defaultDirectory).walkBottomUp().fold(
                true
                // recursively: lastModified + 4H > time or else delete
            ) { res, it -> ((it.lastModified() + (1000L * 60L * 60L * 4L) > System.currentTimeMillis()) || it.delete() || !it.exists()) && res }
        } catch (t: Throwable) {
            logError(t)
            return false
        }
    }

    @Throws
    suspend fun loadTorrent(link: ExtractorLink, event: ((PlayerEvent) -> Unit)?): TorrentRequest {
        val act = CommonActivity.activity ?: throw IllegalArgumentException("No activity")

        val defaultDirectory = "${act.cacheDir.path}/torrent_tmp"

        // start the client if not active, lazy init
        Aria2Starter.start(
            activity = act,
            Aria2Settings(
                UUID.randomUUID().toString(),
                6800, // https://github.com/devgianlu/aria2lib/blob/d34fd083835775cdf65f170437575604e96b602e/src/main/java/com/gianlu/aria2lib/internal/Aria2.java#L382
                defaultDirectory,
            )
        )

        return playAria2c(link, event)
    }
}