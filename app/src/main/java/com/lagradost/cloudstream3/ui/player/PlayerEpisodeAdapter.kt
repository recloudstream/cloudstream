package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.getDisplayPosition
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.player_episodes_large.view.episode_holder_large
import kotlinx.android.synthetic.main.player_episodes_large.view.episode_progress
import kotlinx.android.synthetic.main.player_episodes_small.view.episode_holder
import kotlinx.android.synthetic.main.result_episode_large.view.*


data class PlayerEpisodeClickEvent(val action: Int, val data: Any)

class PlayerEpisodeAdapter(
    private val items: MutableList<Any> = mutableListOf(),
    private val clickCallback: (PlayerEpisodeClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return PlayerEpisodeCardViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.player_episodes, parent, false),
            clickCallback,
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        println("HOLDER $holder $position")

        when (holder) {
            is PlayerEpisodeCardViewHolder -> {
                holder.bind(items[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateList(newList: List<Any>) {
        println("Updated list $newList")
        val diffResult = DiffUtil.calculateDiff(EpisodeDiffCallback(this.items, newList))
        items.clear()
        items.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class PlayerEpisodeCardViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (PlayerEpisodeClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        @SuppressLint("SetTextI18n")
        fun bind(card: Any) {
            if (card is ResultEpisode) {
                val (parentView, otherView) = if (card.poster == null) {
                    itemView.episode_holder to itemView.episode_holder_large
                } else {
                    itemView.episode_holder_large to itemView.episode_holder
                }

                val episodeText: TextView? = parentView.episode_text
                val episodeFiller: MaterialButton? = parentView.episode_filler
                val episodeRating: TextView? = parentView.episode_rating
                val episodeDescript: TextView? = parentView.episode_descript
                val episodeProgress: ContentLoadingProgressBar? = parentView.episode_progress
                val episodePoster: ImageView? = parentView.episode_poster

                parentView.isVisible = true
                otherView.isVisible = false


                episodeText?.apply {
                    val name =
                        if (card.name == null) "${context.getString(R.string.episode)} ${card.episode}" else "${card.episode}. ${card.name}"

                    text = name
                    isSelected = true
                }

                episodeFiller?.isVisible = card.isFiller == true

                val displayPos = card.getDisplayPosition()
                episodeProgress?.max = (card.duration / 1000).toInt()
                episodeProgress?.progress = (displayPos / 1000).toInt()
                episodeProgress?.isVisible =  displayPos > 0L
                episodePoster?.isVisible = episodePoster?.setImage(card.poster) == true

                if (card.rating != null) {
                    episodeRating?.text = episodeRating?.context?.getString(R.string.rated_format)
                        ?.format(card.rating.toFloat() / 10f)
                } else {
                    episodeRating?.text = ""
                }

                episodeRating?.isGone = episodeRating?.text.isNullOrBlank()

                episodeDescript?.apply {
                    text = card.description.html()
                    isGone = text.isNullOrBlank()
                    //setOnClickListener {
                    //    clickCallback.invoke(PlayerEpisodeClickEvent(ACTION_SHOW_DESCRIPTION, card))
                    //}
                }

                parentView.setOnClickListener {
                    clickCallback.invoke(PlayerEpisodeClickEvent(0, card))
                }

                if (parentView.context.isTrueTvSettings()) {
                    parentView.isFocusable = true
                    parentView.isFocusableInTouchMode = true
                    parentView.touchscreenBlocksFocus = false
                }
            }
        }
    }
}

class EpisodeDiffCallback(
    private val oldList: List<Any>,
    private val newList: List<Any>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val a = oldList[oldItemPosition]
        val b = newList[newItemPosition]
        return if (a is ResultEpisode && b is ResultEpisode) {
            a.id == b.id
        } else {
            a == b
        }
    }

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}