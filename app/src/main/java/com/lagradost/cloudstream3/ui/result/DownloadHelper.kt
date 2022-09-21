package com.lagradost.cloudstream3.ui.result

import android.net.Uri
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadEpisodeClickEvent
import com.lagradost.cloudstream3.ui.player.DownloadFileGenerator
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.fetchbutton.aria2c.DownloadStatusTell
import com.lagradost.fetchbutton.ui.PieFetchButton

object DownloadHelper {
    fun PieFetchButton.play(card: ResultEpisode) {
        val files = this.getVideos()
        DownloadFileGenerator(
            files.map { path ->
                ExtractorUri(
                    uri = Uri.parse(path),

                    id = card.id,
                    parentId = card.parentId,
                    name = context.getString(R.string.downloaded_file), //click.data.name ?: keyInfo.displayName
                    season = card.season,
                    episode = card.episode,
                    headerName = card.headerName,
                    tvType = card.tvType,

                    //basePath = keyInfo.basePath,
                    //displayName = keyInfo.displayName,
                    //relativePath = keyInfo.relativePath,
                )
            }
        )
    }

    fun PieFetchButton.setUp(
        card: ResultEpisode,
        downloadClickCallback: (DownloadEpisodeClickEvent) -> Unit
    ) {
        setPersistentId(card.id.toLong())

        setOnClickListener { view ->
            if (view !is PieFetchButton) return@setOnClickListener
            when (view.currentStatus) {
                null, DownloadStatusTell.Removed -> {
                    view.setStatus(DownloadStatusTell.Waiting)
                    downloadClickCallback.invoke(
                        DownloadEpisodeClickEvent(
                            DOWNLOAD_ACTION_DOWNLOAD,
                            card
                        )
                    )
                }
                DownloadStatusTell.Paused -> {
                    view.popupMenuNoIcons(
                        listOf(
                            1 to R.string.resume,
                            2 to R.string.play_episode,
                            3 to R.string.delete
                        )
                    ) {
                        when (itemId) {
                            1 -> if (!view.resumeDownload()) {
                                downloadClickCallback.invoke(
                                    DownloadEpisodeClickEvent(
                                        DOWNLOAD_ACTION_DOWNLOAD,
                                        card
                                    )
                                )
                            }
                            2 -> {
                                play(card)
                            }
                            3 -> {
                                view.deleteAllFiles()
                            }
                        }
                    }
                }
                DownloadStatusTell.Complete -> {
                    view.popupMenuNoIcons(
                        listOf(
                            2 to R.string.play_episode,
                            3 to R.string.delete
                        )
                    ) {
                        when (itemId) {
                            2 -> {
                                play(card)
                            }
                            3 -> {
                                view.deleteAllFiles()
                            }
                        }
                    }
                }
                DownloadStatusTell.Active -> {
                    view.popupMenuNoIcons(
                        listOf(
                            4 to R.string.pause,
                            2 to R.string.play_episode,
                            3 to R.string.delete
                        )
                    ) {
                        when (itemId) {
                            4 -> {
                                view.pauseDownload()
                            }
                            2 -> {
                                play(card)
                            }
                            3 -> {
                                view.deleteAllFiles()
                            }
                        }
                    }
                }
                DownloadStatusTell.Error -> {
                    view.redownload()
                }
                DownloadStatusTell.Waiting -> {

                }
                else -> {}
            }
        }

    }
}