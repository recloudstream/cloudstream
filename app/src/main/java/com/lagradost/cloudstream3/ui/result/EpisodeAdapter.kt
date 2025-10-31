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
import coil3.dispose
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.databinding.ResultEpisodeBinding
import com.lagradost.cloudstream3.databinding.ResultEpisodeLargeBinding
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.secondsToReadable
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_LONG_CLICK
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.Coroutines.main
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
) : NoStateAdapter<ResultEpisode>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.id == b.id
}, contentSame = { a, b ->
    a == b
})) {
    companion object {
        const val HAS_POSTER: Int = 0
        const val HAS_NO_POSTER: Int = 1
        fun getPlayerAction(context: Context): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
            val playerPref =
                settingsManager.getString(context.getString(R.string.player_default_key), "")

            return VideoClickActionHolder.uniqueIdToId(playerPref) ?: ACTION_PLAY_EPISODE_IN_PLAYER
        }

        val sharedPool =
            RecyclerView.RecycledViewPool()
                .apply {
                    this.setMaxRecycledViews(HAS_POSTER or CONTENT, 10)
                    this.setMaxRecycledViews(HAS_NO_POSTER or CONTENT, 10)
                }
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        if (holder.itemView.hasFocus()) {
            holder.itemView.clearFocus()
        }

        when (val binding = holder.view) {
            is ResultEpisodeLargeBinding -> {
                clearImage(binding.episodePoster)
            }
        }
        super.onClearView(holder)
    }

    override fun customContentViewType(item: ResultEpisode): Int =
        if (item.poster.isNullOrBlank() && item.description.isNullOrBlank()) HAS_NO_POSTER else HAS_POSTER

    override fun onCreateCustomContent(parent: ViewGroup, viewType: Int): ViewHolderState<Any> {
        return when (viewType) {
            HAS_NO_POSTER -> {
                ViewHolderState(
                    ResultEpisodeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }

            HAS_POSTER -> {
                ViewHolderState(
                    ResultEpisodeLargeBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }

            else -> throw NotImplementedError()
        }
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: ResultEpisode, position: Int) {
        val itemView = holder.itemView
        when (val binding = holder.view) {
            is ResultEpisodeLargeBinding -> {
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
                            name = item.name,
                            poster = item.poster,
                            episode = item.episode,
                            season = item.season,
                            id = item.id,
                            parentId = item.parentId,
                            score = item.score,
                            description = item.description,
                            cacheTime = System.currentTimeMillis(),
                        ), null
                    ) {
                        when (it.action) {
                            DOWNLOAD_ACTION_DOWNLOAD -> {
                                clickCallback.invoke(
                                    EpisodeClickEvent(
                                        position,
                                        ACTION_DOWNLOAD_EPISODE,
                                        item
                                    )
                                )
                            }

                            DOWNLOAD_ACTION_LONG_CLICK -> {
                                clickCallback.invoke(
                                    EpisodeClickEvent(
                                        position,
                                        ACTION_DOWNLOAD_MIRROR,
                                        item
                                    )
                                )
                            }

                            else -> {
                                downloadClickCallback.invoke(it)
                            }
                        }
                    }

                    val name =
                        if (item.name == null) "${episodeText.context.getString(R.string.episode)} ${item.episode}" else "${item.episode}. ${item.name}"
                    episodeFiller.isVisible = item.isFiller == true
                    episodeText.text =
                        name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
                    episodeText.isSelected = true // is needed for text repeating

                    if (item.videoWatchState == VideoWatchState.Watched) {
                        // This cannot be done in getDisplayPosition() as when you have not watched something
                        // the duration and position is 0
                        //episodeProgress.max = 1
                        //episodeProgress.progress = 1
                        episodePlayIcon.setImageResource(R.drawable.ic_baseline_check_24)
                        episodeProgress.isVisible = false
                    } else {
                        val displayPos = item.getDisplayPosition()
                        val durationSec = (item.duration / 1000).toInt()
                        val progressSec = (displayPos / 1000).toInt()

                        if (displayPos >= item.duration && displayPos > 0) {
                            episodePlayIcon.setImageResource(R.drawable.ic_baseline_check_24)
                            episodeProgress.isVisible = false
                        } else {
                            episodePlayIcon.setImageResource(R.drawable.netflix_play)
                            episodeProgress.apply {
                                max = durationSec
                                progress = progressSec
                                isVisible = displayPos > 0L
                            }
                        }
                    }

                    val posterVisible = !item.poster.isNullOrBlank()
                    if (posterVisible) {
                        val isUpcoming = item.airDate != null && unixTimeMS < item.airDate
                        episodePoster.loadImage(item.poster) {
                            if (isUpcoming) {
                                error {
                                    // If the poster has an url, but it is faulty then
                                    // we use the episodeUpcomingIcon if it is an upcoming episode
                                    main {
                                        // Make sure it is on the main thread
                                        episodeUpcomingIcon.isVisible = true
                                    }

                                    null // We only care about the runnable
                                }
                            }
                        }
                    } else {
                        // Clear the image
                        episodePoster.dispose()
                    }
                    episodePoster.isVisible = posterVisible

                    val rating10p = item.score?.toFloat(10)
                    if (rating10p != null && rating10p > 0.1) {
                        episodeRating.text = episodeRating.context?.getString(R.string.rated_format)
                            ?.format(rating10p) // TODO Change rated_format to use card.score.toString()
                    } else {
                        episodeRating.text = ""
                    }

                    episodeRating.isGone = episodeRating.text.isNullOrBlank()

                    episodeDescript.apply {
                        text = item.description.html()
                        isGone = text.isNullOrBlank()

                        var isExpanded = false
                        setOnClickListener {
                            if (isLayout(TV)) {
                                clickCallback.invoke(
                                    EpisodeClickEvent(
                                        position,
                                        ACTION_SHOW_DESCRIPTION,
                                        item
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

                    if (item.airDate != null) {
                        val isUpcoming = unixTimeMS < item.airDate

                        if (isUpcoming) {
                            episodeProgress.isVisible = false
                            episodePlayIcon.isVisible = false
                            episodeUpcomingIcon.isVisible = !posterVisible
                            episodeDate.setText(
                                txt(
                                    R.string.episode_upcoming_format,
                                    secondsToReadable(
                                        item.airDate.minus(unixTimeMS).div(1000).toInt(),
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
                            }.format(Date(item.airDate))

                            episodeDate.setText(txt(formattedAirDate))
                        }
                    } else {
                        episodeUpcomingIcon.isVisible = false
                        episodePlayIcon.isVisible = true
                        episodeDate.isVisible = false
                    }

                    episodeRuntime.setText(
                        txt(
                            item.runTime?.times(60L)?.toInt()?.let { secondsToReadable(it, "") }
                        )
                    )

                    if (isLayout(EMULATOR or PHONE)) {
                        episodePoster.setOnClickListener {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_CLICK_DEFAULT,
                                    item
                                )
                            )
                        }

                        episodePoster.setOnLongClickListener {
                            clickCallback.invoke(
                                EpisodeClickEvent(
                                    position,
                                    ACTION_SHOW_TOAST,
                                    item
                                )
                            )
                            return@setOnLongClickListener true
                        }
                    }
                }

                itemView.setOnClickListener {
                    clickCallback.invoke(EpisodeClickEvent(position, ACTION_CLICK_DEFAULT, item))
                }

                if (isLayout(TV)) {
                    itemView.isFocusable = true
                    itemView.isFocusableInTouchMode = true
                    //itemView.touchscreenBlocksFocus = false
                }

                itemView.setOnLongClickListener {
                    clickCallback.invoke(EpisodeClickEvent(position, ACTION_SHOW_OPTIONS, item))
                    return@setOnLongClickListener true
                }
            }

            is ResultEpisodeBinding -> {
                binding.episodeHolder.layoutParams.apply {
                    width =
                        if (isLayout(TV or EMULATOR)) TV_EP_SIZE.toPx else ViewGroup.LayoutParams.MATCH_PARENT
                }

                binding.apply {
                    downloadButton.isVisible = hasDownloadSupport
                    downloadButton.setDefaultClickListener(
                        VideoDownloadHelper.DownloadEpisodeCached(
                            name = item.name,
                            poster = item.poster,
                            episode = item.episode,
                            season = item.season,
                            id = item.id,
                            parentId = item.parentId,
                            score = item.score,
                            description = item.description,
                            cacheTime = System.currentTimeMillis(),
                        ), null
                    ) {
                        when (it.action) {
                            DOWNLOAD_ACTION_DOWNLOAD -> {
                                clickCallback.invoke(
                                    EpisodeClickEvent(
                                        position,
                                        ACTION_DOWNLOAD_EPISODE,
                                        item
                                    )
                                )
                            }

                            DOWNLOAD_ACTION_LONG_CLICK -> {
                                clickCallback.invoke(
                                    EpisodeClickEvent(
                                        position,
                                        ACTION_DOWNLOAD_MIRROR,
                                        item
                                    )
                                )
                            }

                            else -> {
                                downloadClickCallback.invoke(it)
                            }
                        }
                    }

                    val name =
                        if (item.name == null) "${episodeText.context.getString(R.string.episode)} ${item.episode}" else "${item.episode}. ${item.name}"
                    episodeFiller.isVisible = item.isFiller == true
                    episodeText.text =
                        name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
                    episodeText.isSelected = true // is needed for text repeating

                    if (item.videoWatchState == VideoWatchState.Watched) {
                        // This cannot be done in getDisplayPosition() as when you have not watched something
                        // the duration and position is 0
                        episodeProgress.max = 1
                        episodeProgress.progress = 1
                        episodeProgress.isVisible = true
                    } else {
                        val displayPos = item.getDisplayPosition()
                        episodeProgress.max = (item.duration / 1000).toInt()
                        episodeProgress.progress = (displayPos / 1000).toInt()
                        episodeProgress.isVisible = displayPos > 0L
                    }

                    itemView.setOnClickListener {
                        clickCallback.invoke(
                            EpisodeClickEvent(
                                position,
                                ACTION_CLICK_DEFAULT,
                                item
                            )
                        )
                    }

                    if (isLayout(TV)) {
                        itemView.isFocusable = true
                        itemView.isFocusableInTouchMode = true
                        //itemView.touchscreenBlocksFocus = false
                    }

                    itemView.setOnLongClickListener {
                        clickCallback.invoke(EpisodeClickEvent(position, ACTION_SHOW_OPTIONS, item))
                        return@setOnLongClickListener true
                    }

                    //binding.resultEpisodeDownload.isVisible = hasDownloadSupport
                    //binding.resultEpisodeProgressDownloaded.isVisible = hasDownloadSupport
                }
            }
        }
    }
}