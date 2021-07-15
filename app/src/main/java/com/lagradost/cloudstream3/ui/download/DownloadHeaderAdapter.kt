package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.android.synthetic.main.download_header_episode.view.*

data class VisualDownloadHeaderCached(
    val currentOngoingDownloads: Int,
    val totalDownloads: Int,
    val totalBytes: Long,
    val data: VideoDownloadHelper.DownloadHeaderCached,
)

data class DownloadHeaderClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadHeaderCached)
data class DownloadClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadEpisodeCached)

class DownloadHeaderAdapter(
    var cardList: List<VisualDownloadHeaderCached>,
    private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadHeaderViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.download_header_episode, parent, false),
            clickCallback
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
    ) : RecyclerView.ViewHolder(itemView) {
        private val poster: ImageView = itemView.download_header_poster
        private val title: TextView = itemView.download_header_title
        private val extraInfo: TextView = itemView.download_header_info
        private val holder: CardView = itemView.episode_holder

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadHeaderCached) {
            val d = card.data
            if (d.poster != null) {

                val glideUrl =
                    GlideUrl(d.poster)

                poster.context.let {
                    Glide.with(it)
                        .load(glideUrl)
                        .into(poster)
                }
            }

            title.text = d.name
            val mbString = "%.1f".format(card.totalBytes / 1000000f)

            extraInfo.text =
                if (d.type.isMovieType())
                    "${mbString}MB" else
                    "${card.totalDownloads} Episode${if (card.totalDownloads == 1) "" else "s"} | ${mbString}MB"

            holder.setOnClickListener {
                clickCallback.invoke(DownloadHeaderClickEvent(0, d))
            }
        }
    }
}
