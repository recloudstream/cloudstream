package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.UIHelper.getGridFormatId
import com.lagradost.cloudstream3.UIHelper.getGridIsCompact
import com.lagradost.cloudstream3.UIHelper.toPx
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import kotlinx.android.synthetic.main.search_result_compact.view.backgroundCard
import kotlinx.android.synthetic.main.search_result_compact.view.imageText
import kotlinx.android.synthetic.main.search_result_compact.view.imageView
import kotlinx.android.synthetic.main.search_result_grid.view.*
import kotlin.math.roundToInt

class SearchAdapter(
    var cardList: List<SearchResponse>,
    private val resView: AutofitRecyclerView,
    private val clickCallback: (SearchResponse) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = parent.context.getGridFormatId()
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            clickCallback,
            resView
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
    constructor(itemView: View, private val clickCallback: (SearchResponse) -> Unit, resView: AutofitRecyclerView) :
        RecyclerView.ViewHolder(itemView) {
        val cardView: ImageView = itemView.imageView
        private val cardText: TextView = itemView.imageText
        private val textType: TextView? = itemView.text_type
        // val search_result_lang: ImageView? = itemView.search_result_lang

        private val textIsDub: View? = itemView.text_is_dub
        private val textIsSub: View? = itemView.text_is_sub

        //val cardTextExtra: TextView? = itemView.imageTextExtra
        //val imageTextProvider: TextView? = itemView.imageTextProvider
        private val bg: CardView = itemView.backgroundCard
        private val compactView = itemView.context.getGridIsCompact()
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        fun bind(card: SearchResponse) {
            if (!compactView) {
                cardView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }
            }

            textType?.text = when (card.type) {
                TvType.Anime -> "Anime"
                TvType.Movie -> "Movie"
                TvType.ONA -> "ONA"
                TvType.TvSeries -> "TV"
            }
            // search_result_lang?.visibility = View.GONE

            textIsDub?.visibility = View.GONE
            textIsSub?.visibility = View.GONE

            cardText.text = card.name

            //imageTextProvider.text = card.apiName
            if (!card.posterUrl.isNullOrEmpty()) {

                val glideUrl =
                    GlideUrl(card.posterUrl)

                Glide.with(cardView.context)
                    .load(glideUrl)
                    .into(cardView)
            }

            bg.setOnClickListener {
                clickCallback.invoke(card)
            }

            when (card) {
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
}
