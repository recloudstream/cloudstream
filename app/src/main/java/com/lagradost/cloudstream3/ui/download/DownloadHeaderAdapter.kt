package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.fetchbutton.ui.PieFetchButton
import kotlinx.android.synthetic.main.download_header_episode.view.*
import java.util.*

data class VisualDownloadHeaderCached(
    val currentOngoingDownloads: Int,
    val totalDownloads: Int,
    override val totalBytes: Long,
    override val currentBytes: Long,
    val header: VideoDownloadHelper.DownloadHeaderCached,
    override val data: VideoDownloadHelper.DownloadEpisodeCached?,
) : IVisualDownloadChildCached

data class DownloadHeaderClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadHeaderCached)

class DownloadHeaderAdapter(
    var cardList: List<VisualDownloadHeaderCached>,
    private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
    private val movieClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadHeaderViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.download_header_episode, parent, false),
            clickCallback,
            movieClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadHeaderViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class DownloadHeaderViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
        private val movieClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val poster: ImageView? = itemView.download_header_poster
        private val title: TextView = itemView.download_header_title
        private val extraInfo: TextView = itemView.download_header_info
        private val holder: CardView = itemView.episode_holder

        private val downloadButton: PieFetchButton = itemView.download_header_download

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadHeaderCached) {
            val d = card.header

            poster?.setImage(d.poster)
            poster?.setOnClickListener {
                clickCallback.invoke(DownloadHeaderClickEvent(1, d))
            }

            title.text = d.name
            val mbString = formatShortFileSize(itemView.context, card.totalBytes)

            //val isMovie = d.type.isMovieType()
            if (card.data != null) {
                downloadButton.isVisible = true
                /*setUpButton(
                    card.currentBytes,
                    card.totalBytes,
                    downloadBar,
                    downloadImage,
                    extraInfo,
                    card.child,
                    movieClickCallback
                )*/
                DownloadButtonSetup.bind(card, downloadButton, extraInfo) { click ->
                    movieClickCallback.invoke(DownloadClickEvent(click.action, card.data))
                }

                holder.setOnClickListener {
                    movieClickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, card.data))
                }
            } else {
                downloadButton.isVisible = false

                try {
                    extraInfo.text =
                        extraInfo.context.getString(R.string.extra_info_format).format(
                            card.totalDownloads,
                            if (card.totalDownloads == 1) extraInfo.context.getString(R.string.episode) else extraInfo.context.getString(
                                R.string.episodes
                            ),
                            mbString
                        )
                } catch (t : Throwable) {
                    // you probably formatted incorrectly
                    extraInfo.text = "Error"
                    logError(t)
                }


                holder.setOnClickListener {
                    clickCallback.invoke(DownloadHeaderClickEvent(0, d))
                }
            }
        }
    }
}
