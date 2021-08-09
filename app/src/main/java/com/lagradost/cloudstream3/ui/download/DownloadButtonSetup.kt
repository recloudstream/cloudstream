package com.lagradost.cloudstream3.ui.download

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.PlayerFragment
import com.lagradost.cloudstream3.ui.player.UriData
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.VideoDownloadManager

object DownloadButtonSetup {
    fun handleDownloadClick(activity: Activity?, headerName: String?, click: DownloadClickEvent) {
        val id = click.data.id
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

                    builder.setTitle("Delete File")
                    builder.setMessage("This will permanently delete ${getNameFull(click.data.name,click.data.episode,click.data.season)}\nAre you sure?")
                        .setTitle("Delete")
                        .setPositiveButton("Delete", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener)
                        .show()
                }
            }
            DOWNLOAD_ACTION_PAUSE_DOWNLOAD -> {
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(click.data.id, VideoDownloadManager.DownloadActionType.Pause)
                )
            }
            DOWNLOAD_ACTION_RESUME_DOWNLOAD -> {
                activity?.let { ctx ->
                    val pkg = VideoDownloadManager.getDownloadResumePackage(ctx, id)
                    if (pkg != null) {
                        VideoDownloadManager.downloadFromResume(ctx, pkg)
                    } else {
                        VideoDownloadManager.downloadEvent.invoke(
                            Pair(click.data.id, VideoDownloadManager.DownloadActionType.Resume)
                        )
                    }
                }
            }
            DOWNLOAD_ACTION_PLAY_FILE -> {
                activity?.let { act ->
                    val info =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(act, click.data.id)
                            ?: return

                    (act as FragmentActivity).supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_anim,
                            R.anim.exit_anim,
                            R.anim.pop_enter,
                            R.anim.pop_exit
                        )
                        .add(
                            R.id.homeRoot,
                            PlayerFragment.newInstance(
                                UriData(
                                    info.path.toString(),
                                    click.data.id,
                                    headerName ?: "null",
                                    if (click.data.episode <= 0) null else click.data.episode,
                                    click.data.season
                                ),
                                act.getViewPos(click.data.id)?.position ?: 0
                            )
                        )
                        .commit()
                }
            }
        }
    }
}