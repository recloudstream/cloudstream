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
import kotlinx.android.synthetic.main.result_episode.view.*


class EpisodeAdapter(
    activity: Activity,
    animeList: ArrayList<ResultEpisode>,
    resView: RecyclerView,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    private var activity: Activity = activity
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.result_episode, parent, false),
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
    constructor(itemView: View, _activity: Activity, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val activity = _activity
        val episode_view_procentage: View = itemView.episode_view_procentage
        val episode_view_procentage_off: View = itemView.episode_view_procentage_off
        val episode_text: TextView = itemView.episode_text
        val episode_extra: ImageView = itemView.episode_extra
        val episode_play: ImageView = itemView.episode_play

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
                getApiFromName(card.apiName).loadLinks(card.data, card.id)
            }
        }
    }
}
