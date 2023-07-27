package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ResultEpisodeBinding
import com.lagradost.cloudstream3.databinding.ResultEpisodeLargeBinding
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
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
const val ACTION_SHOW_DESCRIPTION = 15

const val ACTION_DOWNLOAD_EPISODE_SUBTITLE = 13
const val ACTION_DOWNLOAD_EPISODE_SUBTITLE_MIRROR = 14

const val ACTION_PLAY_EPISODE_IN_WEB_VIDEO = 16
const val ACTION_PLAY_EPISODE_IN_MPV = 17

const val ACTION_MARK_AS_WATCHED = 18
const val TV_EP_SIZE_LARGE = 400
const val TV_EP_SIZE_SMALL = 300
data class EpisodeClickEvent(val action: Int, val data: ResultEpisode)

class EpisodeAdapter(
    private val hasDownloadSupport: Boolean,
    private val clickCallback: (EpisodeClickEvent) -> Unit,
    private val downloadClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        /**
         * @return ACTION_PLAY_EPISODE_IN_PLAYER, ACTION_PLAY_EPISODE_IN_BROWSER or ACTION_PLAY_EPISODE_IN_VLC_PLAYER depending on player settings.
         * See array.xml/player_pref_values
         **/
        fun getPlayerAction(context: Context): Int {

            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            return when (settingsManager.getInt(context.getString(R.string.player_pref_key), 1)) {
                1 -> ACTION_PLAY_EPISODE_IN_PLAYER
                2 -> ACTION_PLAY_EPISODE_IN_VLC_PLAYER
                3 -> ACTION_PLAY_EPISODE_IN_BROWSER
                4 -> ACTION_PLAY_EPISODE_IN_WEB_VIDEO
                5 -> ACTION_PLAY_EPISODE_IN_MPV
                else -> ACTION_PLAY_EPISODE_IN_PLAYER
            }
        }
    }

    var cardList: MutableList<ResultEpisode> = mutableListOf()

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder.itemView.hasFocus()) {
            holder.itemView.clearFocus()
        }
    }

    fun updateList(newList: List<ResultEpisode>) {
        val diffResult = DiffUtil.calculateDiff(
            ResultDiffCallback(this.cardList, newList)
        )

        cardList.clear()
        cardList.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    private fun getItem(position: Int): ResultEpisode {
        return cardList[position]
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.poster.isNullOrBlank()) 0 else 1
    }


    // private val layout = R.layout.result_episode_both

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        /*val layout = if (cardList.filter { it.poster != null }.size >= cardList.size / 2)
            R.layout.result_episode_large
        else R.layout.result_episode*/

        return when (viewType) {
            0 -> {
                EpisodeCardViewHolderSmall(
                    ResultEpisodeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    hasDownloadSupport,
                    clickCallback,
                    downloadClickCallback
                )
            }

            1 -> {
                EpisodeCardViewHolderLarge(
                    ResultEpisodeLargeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    ),
                    hasDownloadSupport,
                    clickCallback,
                    downloadClickCallback
                )
            }

            else -> throw NotImplementedError()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EpisodeCardViewHolderLarge -> {
                holder.bind(getItem(position))
            }

            is EpisodeCardViewHolderSmall -> {
                holder.bind(getItem(position))
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class EpisodeCardViewHolderLarge
    constructor(
        val binding: ResultEpisodeLargeBinding,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        var localCard: ResultEpisode? = null

        @SuppressLint("SetTextI18n")
        fun bind(card: ResultEpisode) {
            localCard = card

            val setWidth =
                if (isTvSettings()) TV_EP_SIZE_LARGE.toPx else ViewGroup.LayoutParams.MATCH_PARENT

            binding.episodeLinHolder.layoutParams.width = setWidth
            binding.episodeHolderLarge.layoutParams.width = setWidth
            binding.episodeHolder.layoutParams.width = setWidth

            val isTrueTv = isTrueTvSettings()

            binding.apply {
                downloadButton.isVisible = hasDownloadSupport
                downloadButton.setDefaultClickListener(
                    VideoDownloadHelper.DownloadEpisodeCached(
                        card.name,
                        card.poster,
                        card.episode,
                        card.season,
                        card.id,
                        card.parentId,
                        card.rating,
                        card.description,
                        System.currentTimeMillis(),
                    ), null
                ) {
                    when (it.action) {
                        DOWNLOAD_ACTION_DOWNLOAD -> {
                            clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, card))
                        }

                        DOWNLOAD_ACTION_LONG_CLICK -> {
                            clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_MIRROR, card))
                        }

                        else -> {
                            downloadClickCallback.invoke(it)
                        }
                    }
                }

                val name =
                    if (card.name == null) "${episodeText.context.getString(R.string.episode)} ${card.episode}" else "${card.episode}. ${card.name}"
                episodeFiller.isVisible = card.isFiller == true
                episodeText.text =
                    name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
                episodeText.isSelected = true // is needed for text repeating

                if (card.videoWatchState == VideoWatchState.Watched) {
                    // This cannot be done in getDisplayPosition() as when you have not watched something
                    // the duration and position is 0
                    episodeProgress.max = 1
                    episodeProgress.progress = 1
                    episodeProgress.isVisible = true
                } else {
                    val displayPos = card.getDisplayPosition()
                    episodeProgress.max = (card.duration / 1000).toInt()
                    episodeProgress.progress = (displayPos / 1000).toInt()
                    episodeProgress.isVisible = displayPos > 0L
                }

                episodePoster.isVisible = episodePoster.setImage(card.poster) == true

                if (card.rating != null) {
                    episodeRating.text = episodeRating.context?.getString(R.string.rated_format)
                        ?.format(card.rating.toFloat() / 10f)
                } else {
                    episodeRating.text = ""
                }

                episodeRating.isGone = episodeRating.text.isNullOrBlank()

                episodeDescript.apply {
                    text = card.description.html()
                    isGone = text.isNullOrBlank()
                    setOnClickListener {
                        clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_DESCRIPTION, card))
                    }
                }

                if (!isTrueTv) {
                    episodePoster.setOnClickListener {
                        clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
                    }

                    episodePoster.setOnLongClickListener {
                        clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_TOAST, card))
                        return@setOnLongClickListener true
                    }
                }
            }
            itemView.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            if (isTrueTv) {
                itemView.isFocusable = true
                itemView.isFocusableInTouchMode = true
                //itemView.touchscreenBlocksFocus = false
            }

            itemView.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                return@setOnLongClickListener true
            }

            //binding.resultEpisodeDownload.isVisible = hasDownloadSupport
            //binding.resultEpisodeProgressDownloaded.isVisible = hasDownloadSupport
        }
    }

    class EpisodeCardViewHolderSmall
    constructor(
        val binding: ResultEpisodeBinding,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(card: ResultEpisode) {
            val isTrueTv = isTrueTvSettings()

            binding.episodeHolder.layoutParams.apply {
                width =
                    if (isTvSettings()) TV_EP_SIZE_SMALL.toPx else ViewGroup.LayoutParams.MATCH_PARENT
            }

            binding.apply {
                downloadButton.isVisible = hasDownloadSupport
                downloadButton.setDefaultClickListener(
                    VideoDownloadHelper.DownloadEpisodeCached(
                        card.name,
                        card.poster,
                        card.episode,
                        card.season,
                        card.id,
                        card.parentId,
                        card.rating,
                        card.description,
                        System.currentTimeMillis(),
                    ), null
                ) {
                    when (it.action) {
                        DOWNLOAD_ACTION_DOWNLOAD -> {
                            clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, card))
                        }

                        DOWNLOAD_ACTION_LONG_CLICK -> {
                            clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_MIRROR, card))
                        }

                        else -> {
                            downloadClickCallback.invoke(it)
                        }
                    }
                }

                val name =
                    if (card.name == null) "${episodeText.context.getString(R.string.episode)} ${card.episode}" else "${card.episode}. ${card.name}"
                episodeFiller.isVisible = card.isFiller == true
                episodeText.text =
                    name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
                episodeText.isSelected = true // is needed for text repeating

                if (card.videoWatchState == VideoWatchState.Watched) {
                    // This cannot be done in getDisplayPosition() as when you have not watched something
                    // the duration and position is 0
                    episodeProgress.max = 1
                    episodeProgress.progress = 1
                    episodeProgress.isVisible = true
                } else {
                    val displayPos = card.getDisplayPosition()
                    episodeProgress.max = (card.duration / 1000).toInt()
                    episodeProgress.progress = (displayPos / 1000).toInt()
                    episodeProgress.isVisible = displayPos > 0L
                }

                itemView.setOnClickListener {
                    clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
                }

                if (isTrueTv) {
                    itemView.isFocusable = true
                    itemView.isFocusableInTouchMode = true
                    //itemView.touchscreenBlocksFocus = false
                }

                itemView.setOnLongClickListener {
                    clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))
                    return@setOnLongClickListener true
                }

                //binding.resultEpisodeDownload.isVisible = hasDownloadSupport
                //binding.resultEpisodeProgressDownloaded.isVisible = hasDownloadSupport
            }
        }
    }
}

class ResultDiffCallback(
    private val oldList: List<ResultEpisode>,
    private val newList: List<ResultEpisode>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].id == newList[newItemPosition].id

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}
