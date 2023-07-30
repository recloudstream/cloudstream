package com.lagradost.cloudstream3.ui.search

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.setImage

object SearchResultBuilder {
    private val showCache: MutableMap<String, Boolean> = mutableMapOf()

    fun updateCache(context: Context?) {
        if (context == null) return
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)

        for (k in context.resources.getStringArray(R.array.poster_ui_options_values)) {
            showCache[k] = settingsManager.getBoolean(k, showCache[k] ?: true)
        }
    }

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
        colorCallback : ((Palette) -> Unit)? = null
    ) {
        val cardView: ImageView = itemView.findViewById(R.id.imageView)
        val cardText: TextView? = itemView.findViewById(R.id.imageText)

        val textIsDub: TextView? = itemView.findViewById(R.id.text_is_dub)
        val textIsSub: TextView? = itemView.findViewById(R.id.text_is_sub)
        val textFlag: TextView? = itemView.findViewById(R.id.text_flag)
        val rating: TextView? = itemView.findViewById(R.id.text_rating)

        val textQuality: TextView? = itemView.findViewById(R.id.text_quality)
        val shadow: View? = itemView.findViewById(R.id.title_shadow)

        val bg: CardView = itemView.findViewById(R.id.background_card)

        val bar: ProgressBar? = itemView.findViewById(R.id.watchProgress)
        val playImg: ImageView? = itemView.findViewById(R.id.search_item_download_play)

        // Do logic

        bar?.isVisible = false
        playImg?.isVisible = false
        textIsDub?.isVisible = false
        textIsSub?.isVisible = false
        textFlag?.isVisible = false
        rating?.isVisible = false

        val showSub = showCache[textIsDub?.context?.getString(R.string.show_sub_key)] ?: false
        val showDub = showCache[textIsDub?.context?.getString(R.string.show_dub_key)] ?: false
        val showTitle = showCache[cardText?.context?.getString(R.string.show_title_key)] ?: false
        val showHd = showCache[textQuality?.context?.getString(R.string.show_hd_key)] ?: false

        if(card is SyncAPI.LibraryItem) {
            val showRating = (card.personalRating ?: 0) != 0
            rating?.isVisible = showRating
            if (showRating) {
                // We want to show 8.5 but not 8.0 hence the replace
                val ratingText = ((card.personalRating ?: 0).toDouble() / 10).toString()
                    .replace(".0", "")

                rating?.text = ratingText
            }
        }

        shadow?.isVisible = showTitle

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
            textQuality?.isVisible = showHd
        } ?: run {
            textQuality?.isVisible = false
        }

        cardText?.text = card.name
        cardText?.isVisible = showTitle
        cardView.isVisible = true

        if (!cardView.setImage(card.posterUrl, card.posterHeaders, colorCallback = colorCallback)) {
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

        bg.isFocusable = false
        bg.isFocusableInTouchMode = false
        if(!isTrueTvSettings()) {
            bg.setOnClickListener {
                click(it)
            }
            bg.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
        }
        //
       //
        //

        itemView.setOnClickListener {
            click(it)
        }
        if (nextFocusUp != null) {
            itemView.nextFocusUpId = nextFocusUp
        }

        if (nextFocusDown != null) {
            itemView.nextFocusDownId = nextFocusDown
        }

        /*when (nextFocusBehavior) {
            true -> itemView.nextFocusLeftId = bg.id
            false -> itemView.nextFocusRightId = bg.id
            null -> {
                bg.nextFocusRightId = -1
                bg.nextFocusLeftId = -1
            }
        }*/

        /*if (nextFocusUp != null) {
            bg.nextFocusUpId = nextFocusUp
        }

        if (nextFocusDown != null) {
            bg.nextFocusDownId = nextFocusDown
        }

        */

        if (isTrueTvSettings()) {
           // bg.isFocusable = true
           // bg.isFocusableInTouchMode = true
           // bg.touchscreenBlocksFocus = false
            itemView.isFocusableInTouchMode = true
            itemView.isFocusable = true
        }

        /**/

        itemView.setOnLongClickListener {
            longClick(it)
            return@setOnLongClickListener true
        }

        /*bg.setOnFocusChangeListener { view, b ->
            focus(view, b)
        }*/

        itemView.setOnFocusChangeListener { view, b ->
            focus(view, b)
        }

        when (card) {
            is LiveSearchResponse -> {
                SubtitleHelper.getFlagFromIso(card.lang)?.let { flagEmoji ->
                    textFlag?.apply {
                        isVisible = true
                        text = flagEmoji
                    }
                }
            }
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
                        textIsDub?.isVisible = showDub
                    }
                    if (dubStatus.contains(DubStatus.Subbed)) {
                        textIsSub?.isVisible = showSub
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