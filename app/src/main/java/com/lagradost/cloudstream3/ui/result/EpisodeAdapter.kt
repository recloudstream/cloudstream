package com.lagradost.cloudstream3.ui.result

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.android.synthetic.main.result_episode.view.*

const val ACTION_PLAY_EPISODE = 1
const val ACTION_RELOAD_EPISODE = 2

data class EpisodeClickEvent(val action: Int, val data: ResultEpisode)

class EpisodeAdapter(
    private var activity: Activity,
    var cardList: ArrayList<ResultEpisode>,
    val resView: RecyclerView,
    val clickCallback: (EpisodeClickEvent) -> Unit,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.result_episode, parent, false),
            activity,
            resView,
            clickCallback
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
        _activity: Activity,
        resView: RecyclerView,
        clickCallback: (EpisodeClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        val activity = _activity
        val episode_view_procentage: View = itemView.episode_view_procentage
        val episode_view_procentage_off: View = itemView.episode_view_procentage_off
        val episode_text: TextView = itemView.episode_text
        val episode_extra: ImageView = itemView.episode_extra
        val episode_play: ImageView = itemView.episode_play
        val clickCallback = clickCallback

        fun bind(card: ResultEpisode) {
            episode_text.text = card.name ?: "Episode ${card.episode}"

            fun setWidth(v: View, procentage: Float) {
                val param = LinearLayout.LayoutParams(
                    v.layoutParams.width,
                    v.layoutParams.height,
                    procentage
                )
                v.layoutParams = param
            }
            setWidth(episode_view_procentage, card.watchProgress)
            setWidth(episode_view_procentage_off, 1 - card.watchProgress)

            episode_play.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_PLAY_EPISODE, card))
            }
        }
    }
}
