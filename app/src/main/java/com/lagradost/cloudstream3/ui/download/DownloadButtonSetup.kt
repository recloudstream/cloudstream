package com.lagradost.cloudstream3.ui.download

import android.app.Activity
import android.content.DialogInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.DownloadFileGenerator
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager

object DownloadButtonSetup {
    fun handleDownloadClick(activity: Activity?, click: DownloadClickEvent) {
        val id = click.data.id
        if (click.data !is VideoDownloadHelper.DownloadEpisodeCached) return
        when (click.action) {
            DOWNLOAD_ACTION_DELETE_FILE -> {
                activity?.let { ctx ->
                    val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    VideoDownloadManager.deleteFileAndUpdateSettings(ctx, id)
                                }
                                DialogInterface.BUTTON_NEGATIVE -> {
                                }
                            }
                        }

                    try {
                        builder.setTitle(R.string.delete_file)
                            .setMessage(
                                ctx.getString(R.string.delete_message).format(
                                    ctx.getNameFull(
                                        click.data.name,
                                        click.data.episode,
                                        click.data.season
                                    )
                                )
                            )
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                        // ye you somehow fucked up formatting did you?
                    }
                }
            }
            DOWNLOAD_ACTION_PAUSE_DOWNLOAD -> {
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(click.data.id, VideoDownloadManager.DownloadActionType.Pause)
                )
            }
            DOWNLOAD_ACTION_RESUME_DOWNLOAD -> {
                activity?.let { ctx ->
                    if (VideoDownloadManager.downloadStatus.containsKey(id) && VideoDownloadManager.downloadStatus[id] == VideoDownloadManager.DownloadType.IsPaused) {
                        VideoDownloadManager.downloadEvent.invoke(
                            Pair(click.data.id, VideoDownloadManager.DownloadActionType.Resume)
                        )
                    } else {
                        val pkg = VideoDownloadManager.getDownloadResumePackage(ctx, id)
                        if (pkg != null) {
                            VideoDownloadManager.downloadFromResumeUsingWorker(ctx, pkg)
                        } else {
                            VideoDownloadManager.downloadEvent.invoke(
                                Pair(click.data.id, VideoDownloadManager.DownloadActionType.Resume)
                            )
                        }
                    }
                }
            }
            DOWNLOAD_ACTION_LONG_CLICK -> {
                activity?.let { act ->
                    val length =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(
                            act,
                            click.data.id
                        )?.fileLength
                            ?: 0
                    if (length > 0) {
                        showToast(act, R.string.delete, Toast.LENGTH_LONG)
                    } else {
                        showToast(act, R.string.download, Toast.LENGTH_LONG)
                    }
                }
            }
            DOWNLOAD_ACTION_PLAY_FILE -> {
                activity?.let { act ->
                    val info =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(
                            act,
                            click.data.id
                        ) ?: return
                    val keyInfo = getKey<VideoDownloadManager.DownloadedFileInfo>(
                        VideoDownloadManager.KEY_DOWNLOAD_INFO,
                        click.data.id.toString()
                    ) ?: return
                    val parent = getKey<VideoDownloadHelper.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        click.data.parentId.toString()
                    ) ?: return

                    act.navigate(
                        R.id.global_to_navigation_player, GeneratorPlayer.newInstance(
                            DownloadFileGenerator(
                                listOf(
                                    ExtractorUri(
                                        uri = info.path,

                                        id = click.data.id,
                                        parentId = click.data.parentId,
                                        name = act.getString(R.string.downloaded_file), //click.data.name ?: keyInfo.displayName
                                        season = click.data.season,
                                        episode = click.data.episode,
                                        headerName = parent.name,
                                        tvType = parent.type,

                                        basePath = keyInfo.basePath,
                                        displayName = keyInfo.displayName,
                                        relativePath = keyInfo.relativePath,
                                    )
                                )
                            )
                        )
                        //R.id.global_to_navigation_player, PlayerFragment.newInstance(
                        //    UriData(
                        //        info.path.toString(),
                        //        keyInfo.basePath,
                        //        keyInfo.relativePath,
                        //        keyInfo.displayName,
                        //        click.data.parentId,
                        //        click.data.id,
                        //        headerName ?: "null",
                        //        if (click.data.episode <= 0) null else click.data.episode,
                        //        click.data.season
                        //    ),
                        //    getViewPos(click.data.id)?.position ?: 0
                        //)
                    )
                }
            }
        }
    }
}