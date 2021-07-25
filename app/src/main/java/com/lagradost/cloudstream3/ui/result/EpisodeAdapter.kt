package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.result_episode.view.episode_holder
import kotlinx.android.synthetic.main.result_episode.view.episode_text
import kotlinx.android.synthetic.main.result_episode_large.view.*

const val ACTION_PLAY_EPISODE_IN_PLAYER = 1
const val ACTION_PLAY_EPISODE_IN_VLC_PLAYER = 2
const val ACTION_PLAY_EPISODE_IN_BROWSER = 3

const val ACTION_CHROME_CAST_EPISODE = 4
const val ACTION_CHROME_CAST_MIRROR = 5

const val ACTION_DOWNLOAD_EPISODE = 6
const val ACTION_DOWNLOAD_MIRROR = 7

const val ACTION_RELOAD_EPISODE = 8
const val ACTION_COPY_LINK = 9

const val ACTION_SHOW_OPTIONS = 10

const val ACTION_CLICK_DEFAULT = 11

data class EpisodeClickEvent(val action: Int, val data: ResultEpisode)

class EpisodeAdapter(
    var cardList: List<ResultEpisode>,
    private val hasDownloadSupport: Boolean,
    private val clickCallback: (EpisodeClickEvent) -> Unit,
    private val downloadClickCallback: (DownloadClickEvent) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    @LayoutRes
    private var layout: Int = 0
    fun updateLayout() {
        layout = if (cardList.filter { it.poster != null }.size >= cardList.size / 2f) // If over half has posters then use the large layout
            R.layout.result_episode_large
        else R.layout.result_episode
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        /*val layout = if (cardList.filter { it.poster != null }.size >= cardList.size / 2)
            R.layout.result_episode_large
        else R.layout.result_episode*/

        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            hasDownloadSupport,
            clickCallback,
            downloadClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(
        itemView: View,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val episodeText: TextView = itemView.episode_text
        private val episodeRating: TextView? = itemView.episode_rating
        private val episodeDescript: TextView? = itemView.episode_descript
        private val episodeProgress: ContentLoadingProgressBar? = itemView.episode_progress
        private val episodePoster: ImageView? = itemView.episode_poster

        private val episodeDownloadBar: ContentLoadingProgressBar = itemView.result_episode_progress_downloaded
        private val episodeDownloadImage: ImageView = itemView.result_episode_download

        private val episodeHolder = itemView.episode_holder

        @SuppressLint("SetTextI18n")
        fun bind(card: ResultEpisode) {
            val name = if (card.name == null) "Episode ${card.episode}" else "${card.episode}. ${card.name}"
            episodeText.text = name

            val watchProgress = card.getWatchProgress()

            episodeProgress?.progress = (watchProgress * 50).toInt()
            episodeProgress?.visibility = if (watchProgress > 0.0f) View.VISIBLE else View.GONE

            if (card.poster != null) {
                episodePoster?.visibility = View.VISIBLE
                if (episodePoster != null) {
                    val glideUrl =
                        GlideUrl(card.poster)
                    Glide.with(episodePoster.context)
                        .load(glideUrl)
                        .into(episodePoster)
                }
            } else {
                episodePoster?.visibility = View.GONE
            }

            if (card.rating != null) {
                episodeRating?.text = "Rated: %.1f".format(card.rating.toFloat() / 10f).replace(",", ".")
            } else {
                episodeRating?.text = ""
            }

            if (card.descript != null) {
                episodeDescript?.visibility = View.VISIBLE
                episodeDescript?.text = card.descript
            } else {
                episodeDescript?.visibility = View.GONE
            }

            episodeHolder.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            episodeHolder.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))

                return@setOnLongClickListener true
            }

            episodeDownloadImage.visibility = if (hasDownloadSupport) View.VISIBLE else View.GONE
            episodeDownloadBar.visibility = if (hasDownloadSupport) View.VISIBLE else View.GONE

            if (hasDownloadSupport) {
                val downloadInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(itemView.context, card.id)

                DownloadButtonSetup.setUpButton(
                    downloadInfo?.fileLength, downloadInfo?.totalBytes, episodeDownloadBar, episodeDownloadImage, null,
                    VideoDownloadHelper.DownloadEpisodeCached(
                        card.name, card.poster, card.episode, card.season, card.id, 0, card.rating, card.descript
                    )
                ) {
                    if (it.action == DOWNLOAD_ACTION_DOWNLOAD) {
                        clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, card))
                    } else {
                        downloadClickCallback.invoke(it)
                    }
                }
            }
        }
    }
}
