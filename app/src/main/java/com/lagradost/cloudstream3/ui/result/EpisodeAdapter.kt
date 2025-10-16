package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.databinding.ResultEpisodeBinding
import com.lagradost.cloudstream3.databinding.ResultEpisodeLargeBinding
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.secondsToReadable
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ids >= 1000 are reserved for VideoClickActions
 * @see VideoClickActionHolder
 */
const val ACTION_PLAY_EPISODE_IN_PLAYER = 1
const val ACTION_CHROME_CAST_EPISODE = 4
const val ACTION_CHROME_CAST_MIRROR = 5

const val ACTION_DOWNLOAD_EPISODE = 6
const val ACTION_DOWNLOAD_MIRROR = 7

const val ACTION_RELOAD_EPISODE = 8

const val ACTION_SHOW_OPTIONS = 10

const val ACTION_CLICK_DEFAULT = 11
const val ACTION_SHOW_TOAST = 12
const val ACTION_SHOW_DESCRIPTION = 15

const val ACTION_DOWNLOAD_EPISODE_SUBTITLE = 13
const val ACTION_DOWNLOAD_EPISODE_SUBTITLE_MIRROR = 14

const val ACTION_MARK_AS_WATCHED = 18

const val TV_EP_SIZE = 400
const val ACTION_MARK_WATCHED_UP_TO_THIS_EPISODE = 19

data class EpisodeClickEvent(val position: Int?, val action: Int, val data: ResultEpisode) {
    constructor(action: Int, data: ResultEpisode) : this(null, action, data)
}

class EpisodeAdapter(
    private val hasDownloadSupport: Boolean,
    private val clickCallback: (EpisodeClickEvent) -> Unit,
    private val downloadClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        fun getPlayerAction(context: Context): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            val playerPref =
                settingsManager.getString(context.getString(R.string.player_default_key), "")

            return VideoClickActionHolder.uniqueIdToId(playerPref) ?: ACTION_PLAY_EPISODE_IN_PLAYER
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
        return if (item.poster.isNullOrBlank() && item.description.isNullOrBlank()) 0 else 1
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
                holder.bind(position, getItem(position))
            }

            is EpisodeCardViewHolderSmall -> {
                holder.bind(position, getItem(position))
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class EpisodeCardViewHolderLarge(
        val binding: ResultEpisodeLargeBinding,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        var localCard: ResultEpisode? = null

        @SuppressLint("SetTextI18n")
        fun bind(position: Int, card: ResultEpisode) {
            localCard = card
            val setWidth =
                if (isLayout(TV or EMULATOR)) TV_EP_SIZE.toPx else ViewGroup.LayoutParams.MATCH_PARENT

            binding.apply {
                episodeLinHolder.layoutParams.width = setWidth
                episodeHolderLarge.layoutParams.width = setWidth
                episodeHolder.layoutParams.width = setWidth

                if (isLayout(PHONE or EMULATOR) && CommonActivity.appliedTheme == R.style.AmoledMode) {
                    episodeHolderLarge.radius = 0.0f
                    episodeHolder.setPadding(0)
                }

                downloadButton.isVisible = hasDownloadSupport
                downloadButton.setDefaultClickListener(
                    VideoDownloadHelper.DownloadEpisodeCached(
                        name = card.name,
                        poster = card.poster,
                        episode = card.episode,
                        season = card.season,
                        id = card.id,
                        parentId = card.parentId,
                        score = card.score,
                        description = card.description,
                        cacheTime = System.currentTimeMillis(),
                    ), null
                ) {
                    when (it.action) {
                        DOWNLOAD_ACTION_DOWNLOAD -> {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_DOWNLOAD_EPISODE,
                                    card
                                )
                            )
                        }

                        DOWNLOAD_ACTION_LONG_CLICK -> {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_DOWNLOAD_MIRROR,
                                    card
                                )
                            )
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

                val posterVisible = !card.poster.isNullOrBlank()
                if(posterVisible) {
                    episodePoster.loadImage(card.poster)
                } else {
                    // Clear the image
                    episodePoster.load(null)
                }
                episodePoster.isVisible = posterVisible

                val rating10p = card.score?.toFloat(10)
                if (rating10p != null && rating10p > 0.1) {
                    episodeRating.text = episodeRating.context?.getString(R.string.rated_format)
                        ?.format(rating10p) // TODO Change rated_format to use card.score.toString()
                } else {
                    episodeRating.text = ""
                }

                episodeRating.isGone = episodeRating.text.isNullOrBlank()

                episodeDescript.apply {
                    text = card.description.html()
                    isGone = text.isNullOrBlank()

                    var isExpanded = false
                    setOnClickListener {
                        if (isLayout(TV)) {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_SHOW_DESCRIPTION,
                                    card
                                )
                            )
                        } else {
                            isExpanded = !isExpanded
                            maxLines = if (isExpanded) {
                                Integer.MAX_VALUE
                            } else 4
                        }
                    }
                }

                if (card.airDate != null) {
                    val isUpcoming = unixTimeMS < card.airDate

                    if (isUpcoming) {
                        episodeProgress.isVisible = false
                        episodePlayIcon.isVisible = false
                        episodeUpcomingIcon.isVisible = !posterVisible
                        episodeDate.setText(
                            txt(
                                R.string.episode_upcoming_format,
                                secondsToReadable(
                                    card.airDate.minus(unixTimeMS).div(1000).toInt(),
                                    ""
                                )
                            )
                        )
                    } else {
                        episodePlayIcon.isVisible = true
                        episodeUpcomingIcon.isVisible = false

                        val formattedAirDate = SimpleDateFormat.getDateInstance(
                            DateFormat.LONG,
                            Locale.getDefault()
                        ).apply {
                        }.format(Date(card.airDate))

                        episodeDate.setText(txt(formattedAirDate))
                    }
                } else {
                    episodeUpcomingIcon.isVisible = false
                    episodePlayIcon.isVisible = true
                    episodeDate.isVisible = false
                }

                episodeRuntime.setText(
                    txt(
                        card.runTime?.times(60L)?.toInt()?.let { secondsToReadable(it, "") }
                    )
                )

                if (isLayout(EMULATOR or PHONE)) {
                    episodePoster.setOnClickListener {
                        clickCallback.invoke(
                            EpisodeClickEvent(
                                position,
                                ACTION_CLICK_DEFAULT,
                                card
                            )
                        )
                    }

                    episodePoster.setOnLongClickListener {
                        clickCallback.invoke(EpisodeClickEvent(position, ACTION_SHOW_TOAST, card))
                        return@setOnLongClickListener true
                    }
                }
            }

            itemView.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(position, ACTION_CLICK_DEFAULT, card))
            }

            if (isLayout(TV)) {
                itemView.isFocusable = true
                itemView.isFocusableInTouchMode = true
                //itemView.touchscreenBlocksFocus = false
            }

            itemView.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(position, ACTION_SHOW_OPTIONS, card))
                return@setOnLongClickListener true
            }

            //binding.resultEpisodeDownload.isVisible = hasDownloadSupport
            //binding.resultEpisodeProgressDownloaded.isVisible = hasDownloadSupport
        }
    }

    class EpisodeCardViewHolderSmall(
        val binding: ResultEpisodeBinding,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(position: Int, card: ResultEpisode) {
            binding.episodeHolder.layoutParams.apply {
                width =
                    if (isLayout(TV or EMULATOR)) TV_EP_SIZE.toPx else ViewGroup.LayoutParams.MATCH_PARENT
            }

            binding.apply {
                downloadButton.isVisible = hasDownloadSupport
                downloadButton.setDefaultClickListener(
                    VideoDownloadHelper.DownloadEpisodeCached(
                        name = card.name,
                        poster = card.poster,
                        episode = card.episode,
                        season = card.season,
                        id = card.id,
                        parentId = card.parentId,
                        score = card.score,
                        description = card.description,
                        cacheTime = System.currentTimeMillis(),
                    ), null
                ) {
                    when (it.action) {
                        DOWNLOAD_ACTION_DOWNLOAD -> {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_DOWNLOAD_EPISODE,
                                    card
                                )
                            )
                        }

                        DOWNLOAD_ACTION_LONG_CLICK -> {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_DOWNLOAD_MIRROR,
                                    card
                                )
                            )
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
                    clickCallback.invoke(EpisodeClickEvent(position, ACTION_CLICK_DEFAULT, card))
                }

                if (isLayout(TV)) {
                    itemView.isFocusable = true
                    itemView.isFocusableInTouchMode = true
                    //itemView.touchscreenBlocksFocus = false
                }

                itemView.setOnLongClickListener {
                    clickCallback.invoke(EpisodeClickEvent(position, ACTION_SHOW_OPTIONS, card))
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
