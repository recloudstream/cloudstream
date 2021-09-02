package com.lagradost.cloudstream3.ui.search

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.utils.AppUtils.getNameFull
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.home_result_grid.view.*

object SearchResultBuilder {
    fun bind(
        clickCallback: (SearchClickCallback) -> Unit,
        card: SearchResponse,
        itemView: View
    ) {
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText

        val textIsDub: View? = itemView.text_is_dub
        val textIsSub: View? = itemView.text_is_sub

        val bg: CardView = itemView.backgroundCard

        val bar: ProgressBar? = itemView.watchProgress
        val playImg: ImageView? = itemView.search_item_download_play

        // Do logic

        bar?.visibility = View.GONE
        playImg?.visibility = View.GONE
        textIsDub?.visibility = View.GONE
        textIsSub?.visibility = View.GONE

        cardText.text = card.name

        //imageTextProvider.text = card.apiName
        cardView.setImage(card.posterUrl)

        bg.setOnClickListener {
            clickCallback.invoke(SearchClickCallback(if(card is DataStoreHelper.ResumeWatchingResult) SEARCH_ACTION_PLAY_FILE else SEARCH_ACTION_LOAD, it, card))
        }

        bg.setOnLongClickListener {
            clickCallback.invoke(SearchClickCallback(SEARCH_ACTION_SHOW_METADATA, it, card))
            return@setOnLongClickListener true
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

                if (!card.type.isMovieType()) {
                    cardText.text = cardText.context.getNameFull(card.name, card.episode, card.season)
                }
            }
            is AnimeSearchResponse -> {
                if (card.dubStatus?.size == 1) {
                    //search_result_lang?.visibility = View.VISIBLE
                    if (card.dubStatus.contains(DubStatus.Dubbed)) {
                        textIsDub?.visibility = View.VISIBLE
                        //search_result_lang?.setColorFilter(ContextCompat.getColor(activity, R.color.dubColor))
                    } else if (card.dubStatus.contains(DubStatus.Subbed)) {
                        //search_result_lang?.setColorFilter(ContextCompat.getColor(activity, R.color.subColor))
                        textIsSub?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}