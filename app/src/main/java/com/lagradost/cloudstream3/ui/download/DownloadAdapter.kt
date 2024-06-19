package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DownloadChildEpisodeBinding
import com.lagradost.cloudstream3.databinding.DownloadHeaderEpisodeBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper


const val DOWNLOAD_ACTION_PLAY_FILE = 0
const val DOWNLOAD_ACTION_DELETE_FILE = 1
const val DOWNLOAD_ACTION_RESUME_DOWNLOAD = 2
const val DOWNLOAD_ACTION_PAUSE_DOWNLOAD = 3
const val DOWNLOAD_ACTION_DOWNLOAD = 4
const val DOWNLOAD_ACTION_LONG_CLICK = 5

abstract class VisualDownloadCachedAbstract(
    open val currentBytes: Long,
    open val totalBytes: Long,
    open val data: VideoDownloadHelper.DownloadCachedAbstract
)

data class VisualDownloadChildCached(
    override val currentBytes: Long,
    override val totalBytes: Long,
    override val data: VideoDownloadHelper.DownloadEpisodeCached,
): VisualDownloadCachedAbstract(currentBytes, totalBytes, data)

data class VisualDownloadHeaderCached(
    val currentOngoingDownloads: Int,
    val totalDownloads: Int,
    override val totalBytes: Long,
    override val currentBytes: Long,
    override val data: VideoDownloadHelper.DownloadHeaderCached,
    val child: VideoDownloadHelper.DownloadEpisodeCached?,
): VisualDownloadCachedAbstract(currentBytes, totalBytes, data)

data class DownloadClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadEpisodeCached
)

data class DownloadHeaderClickEvent(
    val action: Int,
    val data: VideoDownloadHelper.DownloadHeaderCached
)

class DownloadAdapter(
    var cardList: MutableList<VisualDownloadCachedAbstract>,
    private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
    private val movieClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHILD = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DownloadViewHolder(
            binding = when (viewType) {
                VIEW_TYPE_HEADER -> {
                    DownloadHeaderEpisodeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
                VIEW_TYPE_CHILD -> {
                    DownloadChildEpisodeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                }
                else -> throw IllegalArgumentException("Invalid view type")
            },
            clickCallback,
            movieClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DownloadViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    var viewType = 0

    override fun getItemViewType(position: Int): Int {
        if (viewType != 0) {
            return viewType
        }

        val isEpisodeBased = cardList[position] !is VisualDownloadHeaderCached
        return if (isEpisodeBased) VIEW_TYPE_CHILD else VIEW_TYPE_HEADER
    }

    override fun getItemCount(): Int {
        return cardList.count()
    }

    class DownloadViewHolder(
        private val binding: ViewBinding,
        private val clickCallback: (DownloadHeaderClickEvent) -> Unit,
        private val movieClickCallback: (DownloadClickEvent) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {

        /*private val poster: ImageView? = itemView.download_header_poster
        private val title: TextView = itemView.download_header_title
        private val extraInfo: TextView = itemView.download_header_info
        private val holder: CardView = itemView.episode_holder

        private val downloadBar: ContentLoadingProgressBar = itemView.download_header_progress_downloaded
        private val downloadImage: ImageView = itemView.download_header_episode_download
        private val normalImage: ImageView = itemView.download_header_goto_child*/

        @SuppressLint("SetTextI18n")
        fun bind(card: VisualDownloadCachedAbstract) {
            when (binding) {
                is DownloadHeaderEpisodeBinding -> binding.apply {
                    if (card !is VisualDownloadHeaderCached) return
                    val d = card.data

                    downloadHeaderPoster.apply {
                        setImage(d.poster)
                        setOnClickListener {
                            clickCallback.invoke(DownloadHeaderClickEvent(1, d))
                        }
                    }

                    downloadHeaderTitle.text = d.name
                    val mbString = formatShortFileSize(itemView.context, card.totalBytes)

                    //val isMovie = d.type.isMovieType()
                    if (card.child != null) {
                        //downloadHeaderProgressDownloaded.visibility = View.VISIBLE

                        // downloadHeaderEpisodeDownload.visibility = View.VISIBLE
                        downloadHeaderGotoChild.visibility = View.GONE

                        downloadButton.setDefaultClickListener(
                            card.child,
                            downloadHeaderInfo,
                            movieClickCallback
                        )
                        downloadButton.isVisible = true
                        /* setUpButton(
                            card.currentBytes,
                            card.totalBytes,
                            downloadBar,
                            downloadImage,
                            extraInfo,
                            card.child,
                            movieClickCallback
                        ) */

                        episodeHolder.setOnClickListener {
                            movieClickCallback.invoke(
                                DownloadClickEvent(
                                    DOWNLOAD_ACTION_PLAY_FILE,
                                    card.child
                                )
                            )
                        }
                    } else {
                        downloadButton.isVisible = false
                        // downloadHeaderProgressDownloaded.visibility = View.GONE
                        // downloadHeaderEpisodeDownload.visibility = View.GONE
                        downloadHeaderGotoChild.visibility = View.VISIBLE

                        try {
                            downloadHeaderInfo.text =
                                downloadHeaderInfo.context.getString(R.string.extra_info_format)
                                    .format(
                                        card.totalDownloads,
                                        if (card.totalDownloads == 1) downloadHeaderInfo.context.getString(
                                            R.string.episode
                                        ) else downloadHeaderInfo.context.getString(
                                            R.string.episodes
                                        ),
                                        mbString
                                    )
                        } catch (t: Throwable) {
                            // you probably formatted incorrectly
                            downloadHeaderInfo.text = "Error"
                            logError(t)
                        }


                        episodeHolder.setOnClickListener {
                            clickCallback.invoke(DownloadHeaderClickEvent(0, d))
                        }
                    }
                }
                is DownloadChildEpisodeBinding -> binding.apply {
                    if (card !is VisualDownloadChildCached) return
                    val d = card.data

                    val posDur = DataStoreHelper.getViewPos(d.id)
                    downloadChildEpisodeProgress.apply {
                        if (posDur != null) {
                            val visualPos = posDur.fixVisual()
                            max = (visualPos.duration / 1000).toInt()
                            progress = (visualPos.position / 1000).toInt()
                            visibility = View.VISIBLE
                        } else {
                            visibility = View.GONE
                        }
                    }

                    downloadButton.setDefaultClickListener(card.data, downloadChildEpisodeTextExtra, movieClickCallback)

                    downloadChildEpisodeText.apply {
                        text = context.getNameFull(d.name, d.episode, d.season)
                        isSelected = true // is needed for text repeating
                    }

                    downloadChildEpisodeHolder.setOnClickListener {
                        movieClickCallback.invoke(DownloadClickEvent(DOWNLOAD_ACTION_PLAY_FILE, d))
                    }
                }
            }
        }
    }
}