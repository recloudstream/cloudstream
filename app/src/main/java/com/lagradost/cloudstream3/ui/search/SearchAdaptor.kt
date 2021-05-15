package com.lagradost.cloudstream3.ui.search

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.UIHelper.getGridFormatId
import com.lagradost.cloudstream3.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.UIHelper.loadResult
import com.lagradost.cloudstream3.UIHelper.toPx
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import kotlinx.android.synthetic.main.search_result_compact.view.*
import kotlinx.android.synthetic.main.search_result_compact.view.backgroundCard
import kotlinx.android.synthetic.main.search_result_compact.view.imageText
import kotlinx.android.synthetic.main.search_result_compact.view.imageView
import kotlinx.android.synthetic.main.search_result_grid.view.*
import kotlin.math.roundToInt

class SearchAdapter(
    activity: Activity,
    animeList: ArrayList<Any>,
    resView: AutofitRecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    private var activity: Activity = activity
    var resView: AutofitRecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = activity.getGridFormatId()
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            activity,
            resView!!
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
    constructor(itemView: View, _activity: Activity, resView: AutofitRecyclerView) : RecyclerView.ViewHolder(itemView) {
        val activity = _activity
        val cardView: ImageView = itemView.imageView
        val cardText: TextView = itemView.imageText
        val text_type: TextView? = itemView.text_type
        val text_is_dub: TextView? = itemView.text_is_dub
        val text_is_sub: TextView? = itemView.text_is_sub

        //val cardTextExtra: TextView? = itemView.imageTextExtra
        //val imageTextProvider: TextView? = itemView.imageTextProvider
        val bg = itemView.backgroundCard
        val compactView = activity.getGridIsCompact()
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        fun bind(card: Any) {
            if (card is SearchResponse) { // GENERIC
                if (!compactView) {
                    cardView.apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            coverHeight
                        )
                    }
                }

                text_type?.text = when (card.type) {
                    TvType.Anime -> "Anime"
                    TvType.Movie -> "Movie"
                    TvType.ONA -> "ONA"
                    TvType.TvSeries -> "TV"
                }

                text_is_dub?.visibility = View.GONE
                text_is_sub?.visibility = View.GONE

                cardText.text = card.name

                //imageTextProvider.text = card.apiName

                val glideUrl =
                    GlideUrl(card.posterUrl)
                activity.let {
                    Glide.with(it)
                        .load(glideUrl)
                        .into(cardView)
                }

                bg.setOnClickListener {
                    activity.loadResult(card.url, card.apiName)
                }

                when (card) {
                    is AnimeSearchResponse -> {
                        if (card.dubStatus?.contains(DubStatus.HasDub) == true) {
                            text_is_dub?.visibility = View.VISIBLE
                        }
                        if (card.dubStatus?.contains(DubStatus.HasSub) == true) {
                            text_is_sub?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
}
