package com.lagradost.cloudstream3.ui.download

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.DownloadHelper.play
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIcons
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.fetchbutton.aria2c.DownloadListener
import com.lagradost.fetchbutton.aria2c.DownloadStatusTell
import com.lagradost.fetchbutton.ui.PieFetchButton
import kotlinx.android.synthetic.main.download_child_episode.view.*

const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

data class VisualDownloadChildCached(
    val currentBytes: Long,
    val totalBytes: Long,
    val data: VideoDownloadHelper.DownloadEpisodeCached,
)

data class DownloadClickEvent(val action: Int, val data: EasyDownloadButton.IMinimumData)
data class DownloadEpisodeClickEvent(val action: Int, val data: ResultEpisode)

class DownloadChildAdapter(
    var cardList: List<VisualDownloadChildCached>,
    private val clickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadChildViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.download_child_episode, parent, false),
            clickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadChildViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class DownloadChildViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.download_child_episode_text
        private val extraInfo: TextView = itemView.download_child_episode_text_extra
        private val holder: CardView = itemView.download_child_episode_holder
        private val progressBar: ContentLoadingProgressBar =
            itemView.download_child_episode_progress
        private val downloadButton: PieFetchButton = itemView.download_child_episode_download

        private var localCard: VisualDownloadChildCached? = null

        fun bind(card: VisualDownloadChildCached) {
            localCard = card
            val d = card.data

            val posDur = getViewPos(d.id)
            if (posDur != null) {
                val visualPos = posDur.fixVisual()
                progressBar.max = (visualPos.duration / 1000).toInt()
                progressBar.progress = (visualPos.position / 1000).toInt()
                progressBar.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
            }

            title.text = title.context.getNameFull(d.name, d.episode, d.season)
            title.isSelected = true // is needed for text repeating
            //extraInfo.text = card.currentBytes

            fun updateText(downloadBytes: Long, totalBytes: Long) {
                extraInfo?.apply {
                    text =
                        context.getString(R.string.download_size_format).format(
                            Formatter.formatShortFileSize(context, downloadBytes),
                            Formatter.formatShortFileSize(context, totalBytes)
                        )
                }
            }
            updateText(card.currentBytes, card.totalBytes)

            downloadButton.apply {
                val play =
                    R.string.play_episode//if (card.episode <= 0) R.string.play_movie_button else R.string.play_episode

                setPersistentId(d.id.toLong())
                doOnAttach { view ->
                    view.findViewTreeLifecycleOwner()?.let { life ->
                        DownloadListener.observe(life) {
                            gid?.let { realGId ->
                                val meta = DownloadListener.getInfo(realGId)
                                updateText(meta.downloadedLength, meta.totalLength)
                            }
                        }
                    }
                }

                downloadButton.isVisible = when (downloadButton.currentStatus) {
                    null, DownloadStatusTell.Removed -> false
                    else -> true
                }


                downloadButton.setOnClickListener {
                    val view = downloadButton

                    fun delete() {
                        // view.deleteAllFiles()
                        clickCallback.invoke(
                            DownloadClickEvent(
                                DOWNLOAD_ACTION_DELETE_FILE,
                                d
                            )
                        )
                    }

                    //if (view !is PieFetchButton) return@setOnClickListener
                    when (view.currentStatus) {
                        /*null, DownloadStatusTell.Removed -> {
                            view.setStatus(DownloadStatusTell.Waiting)
                            downloadClickCallback.invoke(
                                DownloadEpisodeClickEvent(
                                    DOWNLOAD_ACTION_DOWNLOAD,
                                    card
                                )
                            )
                        }*/
                        DownloadStatusTell.Paused -> {
                            view.popupMenuNoIcons(
                                listOf(
                                    1 to R.string.resume,
                                    2 to play,
                                    3 to R.string.delete
                                )
                            ) {
                                when (itemId) {
                                    1 -> if (!view.resumeDownload()) {
                                        /*downloadClickCallback.invoke(
                                            DownloadEpisodeClickEvent(
                                                DOWNLOAD_ACTION_DOWNLOAD,
                                                card
                                            )
                                        )*/
                                    }
                                    2 -> play(d)
                                    3 -> delete()
                                }
                            }
                        }
                        DownloadStatusTell.Complete -> {
                            view.popupMenuNoIcons(
                                listOf(
                                    2 to play,
                                    3 to R.string.delete
                                )
                            ) {
                                when (itemId) {
                                    2 -> play(d)
                                    3 -> delete()
                                }
                            }
                        }
                        DownloadStatusTell.Active -> {
                            view.popupMenuNoIcons(
                                listOf(
                                    4 to R.string.pause,
                                    2 to play,
                                    3 to R.string.delete
                                )
                            ) {
                                when (itemId) {
                                    4 -> view.pauseDownload()
                                    2 -> play(d)
                                    3 -> delete()
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
            holder.setOnClickListener {
                if (downloadButton.isVisible) {
                    downloadButton.play(d)
                } else {
                    holder.setOnClickListener {
                        clickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, d))
                    }
                }
            }
        }
    }
}
