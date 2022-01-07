package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonViewHolder
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.result_episode.view.*
import kotlinx.android.synthetic.main.result_episode.view.episode_holder
import kotlinx.android.synthetic.main.result_episode.view.episode_text
import kotlinx.android.synthetic.main.result_episode_large.view.*
import kotlinx.android.synthetic.main.result_episode_large.view.episode_filler
import kotlinx.android.synthetic.main.result_episode_large.view.episode_progress
import kotlinx.android.synthetic.main.result_episode_large.view.result_episode_download
import kotlinx.android.synthetic.main.result_episode_large.view.result_episode_progress_downloaded
import java.util.*

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
const val ACTION_SHOW_TOAST = 12

data class EpisodeClickEvent(val action: Int, val data: ResultEpisode)

class EpisodeAdapter(
    var cardList: List<ResultEpisode>,
    private val hasDownloadSupport: Boolean,
    private val clickCallback: (EpisodeClickEvent) -> Unit,
    private val downloadClickCallback: (DownloadClickEvent) -> Unit,
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

    @LayoutRes
    private var layout: Int = 0
    fun updateLayout() {
        layout =
            if (cardList.filter { it.poster != null }.size >= cardList.size / 2f) // If over half has posters then use the large layout
                R.layout.result_episode_large
            else R.layout.result_episode
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        /*val layout = if (cardList.filter { it.poster != null }.size >= cardList.size / 2)
            R.layout.result_episode_large
        else R.layout.result_episode*/

        return EpisodeCardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            hasDownloadSupport,
            clickCallback,
            downloadClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EpisodeCardViewHolder -> {
                holder.bind(cardList[position])
                mBoundViewHolders.add(holder)
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class EpisodeCardViewHolder
    constructor(
        itemView: View,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView), DownloadButtonViewHolder {
        override var downloadButton = EasyDownloadButton()

        private val episodeText: TextView = itemView.episode_text
        private val episodeFiller: MaterialButton? = itemView.episode_filler
        private val episodeRating: TextView? = itemView.episode_rating
        private val episodeDescript: TextView? = itemView.episode_descript
        private val episodeProgress: ContentLoadingProgressBar? = itemView.episode_progress
        private val episodePoster: ImageView? = itemView.episode_poster

        private val episodeDownloadBar: ContentLoadingProgressBar = itemView.result_episode_progress_downloaded
        private val episodeDownloadImage: ImageView = itemView.result_episode_download

        private val episodeHolder = itemView.episode_holder

        var localCard: ResultEpisode? = null

        @SuppressLint("SetTextI18n")
        fun bind(card: ResultEpisode) {
            localCard = card

            val name = if (card.name == null) "${episodeText.context.getString(R.string.episode)} ${card.episode}" else "${card.episode}. ${card.name}"
            episodeFiller?.isVisible = card.isFiller == true
            episodeText.text = name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
            episodeText.isSelected = true // is needed for text repeating

            val displayPos = card.getDisplayPosition()
            episodeProgress?.max = (card.duration / 1000).toInt()
            episodeProgress?.progress = (displayPos / 1000).toInt()

            episodeProgress?.visibility = if (displayPos > 0L) View.VISIBLE else View.GONE

            if (card.poster != null) {
                episodePoster?.visibility = View.VISIBLE
                episodePoster?.setImage(card.poster)
            } else {
                episodePoster?.visibility = View.GONE
            }

            if (card.rating != null) {
                episodeRating?.text = episodeRating?.context?.getString(R.string.rated_format)?.format(card.rating.toFloat() / 10f)
            } else {
                episodeRating?.text = ""
            }

            if (card.description != null) {
                episodeDescript?.visibility = View.VISIBLE
                episodeDescript?.text = card.description
            } else {
                episodeDescript?.visibility = View.GONE
            }

            episodePoster?.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            episodePoster?.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_TOAST, card))
                return@setOnLongClickListener true
            }

            episodeHolder.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            if (episodeHolder.context.isTvSettings()) {
                episodeHolder.isFocusable = true
                episodeHolder.isFocusableInTouchMode = true
                episodeHolder.touchscreenBlocksFocus = false
            }

            episodeHolder.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))

                return@setOnLongClickListener true
            }

            episodeDownloadImage.visibility = if (hasDownloadSupport) View.VISIBLE else View.GONE
            episodeDownloadBar.visibility = if (hasDownloadSupport) View.VISIBLE else View.GONE
        }

        override fun reattachDownloadButton() {
            downloadButton.dispose()
            val card = localCard
            if (hasDownloadSupport && card != null) {
                val downloadInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(itemView.context, card.id)

                downloadButton.setUpButton(
                    downloadInfo?.fileLength, downloadInfo?.totalBytes, episodeDownloadBar, episodeDownloadImage, null,
                    VideoDownloadHelper.DownloadEpisodeCached(
                        card.name,
                        card.poster,
                        card.episode,
                        card.season,
                        card.id,
                        0,
                        card.rating,
                        card.description,
                        System.currentTimeMillis(),
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
