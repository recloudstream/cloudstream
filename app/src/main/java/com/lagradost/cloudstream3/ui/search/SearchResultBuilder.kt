package com.lagradost.cloudstream3.ui.search

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.home_result_grid.view.*

object SearchResultBuilder {
    /**
     * @param nextFocusBehavior True if first, False if last, Null if between.
     * Used to prevent escaping the adapter horizontally (focus wise).
     */
    fun bind(
        clickCallback: (SearchClickCallback) -> Unit,
        card: SearchResponse,
        position: Int,
        itemView: View,
        nextFocusBehavior: Boolean? = null,
        nextFocusUp: Int? = null,
        nextFocusDown: Int? = null,
    ) {
        val cardView: ImageView = itemView.imageView
        val cardText: TextView? = itemView.imageText

        val textIsDub: TextView? = itemView.text_is_dub
        val textIsSub: TextView? = itemView.text_is_sub
        val textQuality: TextView? = itemView.text_quality

        val bg: CardView = itemView.background_card

        val bar: ProgressBar? = itemView.watchProgress
        val playImg: ImageView? = itemView.search_item_download_play

        // Do logic

        bar?.isVisible = false
        playImg?.isVisible = false
        textIsDub?.isVisible = false
        textIsSub?.isVisible = false

        when (card.quality) {
            SearchQuality.BlueRay -> R.string.quality_blueray
            SearchQuality.Cam -> R.string.quality_cam
            SearchQuality.CamRip -> R.string.quality_cam_rip
            SearchQuality.DVD -> R.string.quality_dvd
            SearchQuality.HD -> R.string.quality_hd
            SearchQuality.HQ -> R.string.quality_hq
            SearchQuality.HdCam -> R.string.quality_cam_hd
            SearchQuality.Telecine -> R.string.quality_tc
            SearchQuality.Telesync -> R.string.quality_ts
            SearchQuality.WorkPrint -> R.string.quality_workprint
            SearchQuality.SD -> R.string.quality_sd
            SearchQuality.FourK -> R.string.quality_4k
            SearchQuality.UHD -> R.string.quality_uhd
            SearchQuality.SDR -> R.string.quality_sdr
            SearchQuality.HDR -> R.string.quality_hdr
            SearchQuality.WebRip -> R.string.quality_webrip
            null -> null
        }?.let { textRes ->
            textQuality?.setText(textRes)
            textQuality?.isVisible = true
        } ?: run {
            textQuality?.isVisible = false
        }

        cardText?.text = card.name

        cardView.isVisible = true
        if (!cardView.setImage(card.posterUrl, card.posterHeaders)) {
            cardView.setImageResource(R.drawable.default_cover)
        }

        fun click(view: View?) {
            clickCallback.invoke(
                SearchClickCallback(
                    if (card is DataStoreHelper.ResumeWatchingResult) SEARCH_ACTION_PLAY_FILE else SEARCH_ACTION_LOAD,
                    view ?: return,
                    position,
                    card
                )
            )
        }

        fun longClick(view: View?) {
            clickCallback.invoke(
                SearchClickCallback(
                    SEARCH_ACTION_SHOW_METADATA,
                    view ?: return,
                    position,
                    card
                )
            )
        }

        fun focus(view: View?, focus: Boolean) {
            if (focus) {
                clickCallback.invoke(
                    SearchClickCallback(
                        SEARCH_ACTION_FOCUSED,
                        view ?: return,
                        position,
                        card
                    )
                )
            }
        }

        bg.setOnClickListener {
            click(it)
        }

        itemView.setOnClickListener {
            click(it)
        }

        if (nextFocusUp != null) {
            bg.nextFocusUpId = nextFocusUp
        }

        if (nextFocusDown != null) {
            bg.nextFocusDownId = nextFocusDown
        }

        when (nextFocusBehavior) {
            true -> bg.nextFocusLeftId = bg.id
            false -> bg.nextFocusRightId = bg.id
            null -> {
                bg.nextFocusRightId = -1
                bg.nextFocusLeftId = -1
            }
        }

        if (bg.context.isTrueTvSettings()) {
            bg.isFocusable = true
            bg.isFocusableInTouchMode = true
            bg.touchscreenBlocksFocus = false
            itemView.isFocusableInTouchMode = true
            itemView.isFocusable = true
        }

        bg.setOnLongClickListener {
            longClick(it)
            return@setOnLongClickListener true
        }

        itemView.setOnLongClickListener {
            longClick(it)
            return@setOnLongClickListener true
        }

        bg.setOnFocusChangeListener { view, b ->
            focus(view, b)
        }

        itemView.setOnFocusChangeListener { view, b ->
            focus(view, b)
        }

        when (card) {
            is DataStoreHelper.ResumeWatchingResult -> {
                val pos = card.watchPos?.fixVisual()
                if (pos != null) {
                    bar?.max = (pos.duration / 1000).toInt()
                    bar?.progress = (pos.position / 1000).toInt()
                    bar?.visibility = View.VISIBLE
                }

                playImg?.visibility = View.VISIBLE

                if (card.type?.isMovieType() == false) {
                    cardText?.text =
                        cardText?.context?.getNameFull(card.name, card.episode, card.season)
                }
            }
            is AnimeSearchResponse -> {
                val dubStatus = card.dubStatus
                if (!dubStatus.isNullOrEmpty()) {
                    if (dubStatus.contains(DubStatus.Dubbed)) {
                        textIsDub?.visibility = View.VISIBLE
                    }
                    if (dubStatus.contains(DubStatus.Subbed)) {
                        textIsSub?.visibility = View.VISIBLE
                    }
                }

                val dubEpisodes = card.episodes[DubStatus.Dubbed]
                val subEpisodes = card.episodes[DubStatus.Subbed]

                textIsDub?.apply {
                    val dubText = context.getString(R.string.app_dubbed_text)
                    text = if (dubEpisodes != null && dubEpisodes > 0) {
                        context.getString(R.string.app_dub_sub_episode_text_format)
                            .format(dubText, dubEpisodes)
                    } else {
                        dubText
                    }
                }

                textIsSub?.apply {
                    val subText = context.getString(R.string.app_subbed_text)
                    text = if (subEpisodes != null && subEpisodes > 0) {
                        context.getString(R.string.app_dub_sub_episode_text_format)
                            .format(subText, subEpisodes)
                    } else {
                        subText
                    }
                }
            }
        }
    }
}