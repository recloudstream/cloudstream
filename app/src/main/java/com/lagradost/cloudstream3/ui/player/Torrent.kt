package com.lagradost.cloudstream3.ui.player

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import torrServer.TorrServer
import java.io.File
import java.net.ConnectException
import java.net.URLEncoder

object Torrent {
    var hasAcceptedTorrentForThisSession: Boolean? = null
    private const val TORRENT_SERVER_PATH: String = "torrent_tmp"
    private const val TIMEOUT: Long = 3
    private const val TAG: String = "Torrent"

    /** Cleans up both old aria2c files and newer go server, (even if the new is also self cleaning) */
    @Throws
    fun deleteAllFiles(): Boolean {
        val act = CommonActivity.activity ?: return false
        val defaultDirectory = "${act.cacheDir.path}/$TORRENT_SERVER_PATH"
        return File(defaultDirectory).deleteRecursively()
    }

    private var TORRENT_SERVER_URL = "" // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/main/server.go#L23

    /** Returns true if the server is up */
    private suspend fun echo(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            app.get(
                "$TORRENT_SERVER_URL/echo",
            ).text.isNotEmpty()
        } catch (e: ConnectException) {
            // `Failed to connect to /127.0.0.1:8090` if the server is down
            false
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/shutdown.go#L22
    /** Gracefully shutdown the server.
     * should not be used because I am unable to start it again, and the stopTorrentServer() crashes the app */
    suspend fun shutdown(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            app.get(
                "$TORRENT_SERVER_URL/shutdown",
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Lists all torrents by the server */
    @Throws
    private suspend fun list(): Array<TorrentStatus> {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "list",
            ),
            timeout = TIMEOUT,
            headers = emptyMap()
        ).parsed<Array<TorrentStatus>>()
    }

    /** Drops a single torrent, (I think) this means closing the stream. Returns returns if it is successful */
    private suspend fun drop(hash: String): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            return app.post(
                "$TORRENT_SERVER_URL/torrents",
                json = TorrentRequest(
                    action = "drop",
                    hash = hash
                ),
                timeout = TIMEOUT,
                headers = emptyMap()
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Removes a single torrent from the server registry */
    private suspend fun rem(hash: String): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return false
        }
        return try {
            return app.post(
                "$TORRENT_SERVER_URL/torrents",
                json = TorrentRequest(
                    action = "rem",
                    hash = hash
                ),
                timeout = TIMEOUT,
                headers = emptyMap()
            ).isSuccessful
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }


    /** Removes all torrents from the server, and returns if it is successful */
    suspend fun clearAll(): Boolean {
        if(TORRENT_SERVER_URL.isEmpty()) {
            return true
        }
        return try {
            val items = list()
            var allSuccess = true
            for (item in items) {
                val hash = item.hash
                if (hash == null) {
                    Log.i(TAG, "No hash on ${item.name}")
                    allSuccess = false
                    continue
                }
                if (drop(hash)) {
                    Log.i(TAG, "Successfully dropped ${item.name}")
                } else {
                    Log.i(TAG, "Failed to drop ${item.name}")
                    allSuccess = false
                    continue
                }
                if (rem(hash)) {
                    Log.i(TAG, "Successfully removed ${item.name}")
                } else {
                    Log.i(TAG, "Failed to remove ${item.name}")
                    allSuccess = false
                    continue
                }
            }
            allSuccess
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    /** Gets all the metadata of a torrent, will throw if that hash does not exists
     * https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/torrents.go#L126 */
    @Throws
    suspend fun get(
        hash: String,
    ): TorrentStatus {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "get",
                hash = hash,
            ),
            timeout = TIMEOUT,
            headers = emptyMap()
        ).parsed<TorrentStatus>()
    }

    /** Adds a torrent to the server, this is needed for us to get the hash for further modification, as well as start streaming it*/
    @Throws
    private suspend fun add(url: String): TorrentStatus {
        if(TORRENT_SERVER_URL.isEmpty()) {
            throw ErrorLoadingException("Not initialized")
        }
        return app.post(
            "$TORRENT_SERVER_URL/torrents",
            json = TorrentRequest(
                action = "add",
                link = url,
            ),
            headers = emptyMap()
        ).parsed<TorrentStatus>()
    }

    /** Spins up the torrent server. */
    private suspend fun setup(dir: String): Boolean {
        go.Seq.load()
        if (echo()) {
            return true
        }
        val port = TorrServer.startTorrentServer(dir, 0)
        if(port < 0) {
            return false
        }
        TORRENT_SERVER_URL = "http://127.0.0.1:$port"
        TorrServer.addTrackers(trackers.joinToString(separator = ",\n"))
        return echo()
    }

    /** Transforms a torrent link into a streamable link via the server */
    @Throws
    suspend fun transformLink(link: ExtractorLink): Pair<ExtractorLink, TorrentStatus> {
        val act = CommonActivity.activity ?: throw IllegalArgumentException("No activity")
        val defaultDirectory = "${act.cacheDir.path}/$TORRENT_SERVER_PATH"
        File(defaultDirectory).mkdir()
        if (!setup(defaultDirectory)) {
            throw ErrorLoadingException("Unable to setup the torrent server")
        }
        val status = add(link.url)

        return newExtractorLink(
            source = link.source,
            name = link.name,
            url = status.streamUrl(link.url),
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = ""
            this.quality = link.quality
        } to status
    }

    private val trackers = listOf(
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


    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/torrents.go#L18
    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/main/web/api/route.go#L7
    data class TorrentRequest(
        @JsonProperty("action")
        val action: String,
        @JsonProperty("hash")
        val hash: String = "",
        @JsonProperty("link")
        val link: String = "",
        @JsonProperty("title")
        val title: String = "",
        @JsonProperty("poster")
        val poster: String = "",
        @JsonProperty("data")
        val data: String = "",
        @JsonProperty("save_to_db")
        val saveToDB: Boolean = false,
    )

    // https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/torr/state/state.go#L33
    // omitempty = nullable
    data class TorrentStatus(
        @JsonProperty("title")
        var title: String,
        @JsonProperty("poster")
        var poster: String,
        @JsonProperty("data")
        var data: String?,
        @JsonProperty("timestamp")
        var timestamp: Long,
        @JsonProperty("name")
        var name: String?,
        @JsonProperty("hash")
        var hash: String?,
        @JsonProperty("stat")
        var stat: Int,
        @JsonProperty("stat_string")
        var statString: String,
        @JsonProperty("loaded_size")
        var loadedSize: Long?,
        @JsonProperty("torrent_size")
        var torrentSize: Long?,
        @JsonProperty("preloaded_bytes")
        var preloadedBytes: Long?,
        @JsonProperty("preload_size")
        var preloadSize: Long?,
        @JsonProperty("download_speed")
        var downloadSpeed: Double?,
        @JsonProperty("upload_speed")
        var uploadSpeed: Double?,
        @JsonProperty("total_peers")
        var totalPeers: Int?,
        @JsonProperty("pending_peers")
        var pendingPeers: Int?,
        @JsonProperty("active_peers")
        var activePeers: Int?,
        @JsonProperty("connected_seeders")
        var connectedSeeders: Int?,
        @JsonProperty("half_open_peers")
        var halfOpenPeers: Int?,
        @JsonProperty("bytes_written")
        var bytesWritten: Long?,
        @JsonProperty("bytes_written_data")
        var bytesWrittenData: Long?,
        @JsonProperty("bytes_read")
        var bytesRead: Long?,
        @JsonProperty("bytes_read_data")
        var bytesReadData: Long?,
        @JsonProperty("bytes_read_useful_data")
        var bytesReadUsefulData: Long?,
        @JsonProperty("chunks_written")
        var chunksWritten: Long?,
        @JsonProperty("chunks_read")
        var chunksRead: Long?,
        @JsonProperty("chunks_read_useful")
        var chunksReadUseful: Long?,
        @JsonProperty("chunks_read_wasted")
        var chunksReadWasted: Long?,
        @JsonProperty("pieces_dirtied_good")
        var piecesDirtiedGood: Long?,
        @JsonProperty("pieces_dirtied_bad")
        var piecesDirtiedBad: Long?,
        @JsonProperty("duration_seconds")
        var durationSeconds: Double?,
        @JsonProperty("bit_rate")
        var bitRate: String?,
        @JsonProperty("file_stats")
        var fileStats: List<TorrentFileStat>?,
        @JsonProperty("trackers")
        var trackers: List<String>?,
    ) {
        fun streamUrl(url: String): String {
            val fileName =
                this.fileStats?.first { !it.path.isNullOrBlank() }?.path
                    ?: throw ErrorLoadingException("Null path")

            val index = url.substringAfter("index=").substringBefore("&").toIntOrNull() ?: 0

            //  https://github.com/Diegopyl1209/torrentserver-aniyomi/blob/c18f58e51b6738f053261bc863177078aa9c1c98/web/api/stream.go#L18
            return "$TORRENT_SERVER_URL/stream/${
                URLEncoder.encode(fileName, "utf-8")
            }?link=${this.hash}&index=$index&play"
        }
    }

    data class TorrentFileStat(
        @JsonProperty("id")
        val id: Int?,
        @JsonProperty("path")
        val path: String?,
        @JsonProperty("length")
        val length: Long?,
    )
}