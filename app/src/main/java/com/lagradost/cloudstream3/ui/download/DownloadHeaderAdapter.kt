package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.android.synthetic.main.download_header_episode.view.*
import java.util.*

data class VisualDownloadHeaderCached(
    val currentOngoingDownloads: Int,
    val totalDownloads: Int,
    val totalBytes: Long,
    val currentBytes: Long,
    val data: VideoDownloadHelper.DownloadHeaderCached,
    val child: VideoDownloadHelper.DownloadEpisodeCached?,
)

data class DownloadHeaderClickEvent(val action: Int, val data: VideoDownloadHelper.DownloadHeaderCached)

class DownloadHeaderAdapter(
    var cardList: List<VisualDownloadHeaderCached>,
    private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
    private val movieClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mBoundViewHolders: HashSet<DownloadButtonViewHolder> = HashSet()
    private fun getAllBoundViewHolders(): Set<DownloadButtonViewHolder?>? {
        return Collections.unmodifiableSet(mBoundViewHolders)
    }

    fun killAdapter() {
        getAllBoundViewHolders()?.forEach { view ->
            view?.downloadButton?.dispose()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.downloadButton.dispose()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.downloadButton.dispose()
            mBoundViewHolders.remove(holder)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.reattachDownloadButton()
        }
    }

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
                mBoundViewHolders.add(holder)
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
    ) : RecyclerView.ViewHolder(itemView), DownloadButtonViewHolder {
        override var downloadButton = EasyDownloadButton()

        private val poster: ImageView? = itemView.download_header_poster
        private val title: TextView = itemView.download_header_title
        private val extraInfo: TextView = itemView.download_header_info
        private val holder: CardView = itemView.episode_holder

        private val downloadBar: ContentLoadingProgressBar = itemView.download_header_progress_downloaded
        private val downloadImage: ImageView = itemView.download_header_episode_download
        private val normalImage: ImageView = itemView.download_header_goto_child
        private var localCard: VisualDownloadHeaderCached? = null

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadHeaderCached) {
            localCard = card
            val d = card.data

            poster?.setImage(d.poster)
            poster?.setOnClickListener {
                clickCallback.invoke(DownloadHeaderClickEvent(1, d))
            }

            title.text = d.name
            val mbString = "%.1f".format(card.totalBytes / 1000000f)

            //val isMovie = d.type.isMovieType()
            if (card.child != null) {
                downloadBar.visibility = View.VISIBLE
                downloadImage.visibility = View.VISIBLE
                normalImage.visibility = View.GONE
                /*setUpButton(
                    card.currentBytes,
                    card.totalBytes,
                    downloadBar,
                    downloadImage,
                    extraInfo,
                    card.child,
                    movieClickCallback
                )*/

                holder.setOnClickListener {
                    movieClickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, card.child))
                }
            } else {
                downloadBar.visibility = View.GONE
                downloadImage.visibility = View.GONE
                normalImage.visibility = View.VISIBLE

                try {
                    extraInfo.text =
                        extraInfo.context.getString(R.string.extra_info_format).format(
                            card.totalDownloads,
                            if (card.totalDownloads == 1) extraInfo.context.getString(R.string.episode) else extraInfo.context.getString(
                                R.string.episodes
                            ),
                            mbString
                        )
                } catch (e : Exception) {
                    // you probably formatted incorrectly
                    extraInfo.text = "Error"
                    logError(e)
                }


                holder.setOnClickListener {
                    clickCallback.invoke(DownloadHeaderClickEvent(0, d))
                }
            }
        }

        override fun reattachDownloadButton() {
            downloadButton.dispose()
            val card = localCard
            if (card?.child != null) {
                downloadButton.setUpButton(
                    card.currentBytes,
                    card.totalBytes,
                    downloadBar,
                    downloadImage,
                    extraInfo,
                    card.child,
                    movieClickCallback
                )
            }
        }
    }
}
