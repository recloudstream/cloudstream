package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.cast_item.view.*

class ActorAdaptor(
    private val actors: MutableList<ActorData>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.cast_item, parent, false),
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(actors[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return actors.size
    }

    fun updateList(newList: List<ActorData>) {
        val diffResult = DiffUtil.calculateDiff(
            ActorDiffCallback(this.actors, newList)
        )

        actors.clear()
        actors.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    private class CardViewHolder
    constructor(
        itemView: View,
    ) :
        RecyclerView.ViewHolder(itemView) {
        private val actorImage: ImageView = itemView.actor_image
        private val actorName: TextView = itemView.actor_name
        private val actorExtra: TextView = itemView.actor_extra
        private val voiceActorImage: ImageView = itemView.voice_actor_image
        private val voiceActorImageHolder: View = itemView.voice_actor_image_holder
        private val voiceActorName: TextView = itemView.voice_actor_name

        fun bind(card: ActorData) {
            actorImage.setImage(card.actor.image)
            actorName.text = card.actor.name
            card.role?.let {
                actorExtra.context?.getString(
                    when (it) {
                        ActorRole.Main -> {
                            R.string.actor_main
                        }
                        ActorRole.Supporting -> {
                            R.string.actor_supporting
                        }
                        ActorRole.Background -> {
                            R.string.actor_background
                        }
                    }
                )?.let { text ->
                    actorExtra.isVisible = true
                    actorExtra.text = text
                }
            } ?: card.roleString?.let {
                actorExtra.isVisible = true
                actorExtra.text = it
            } ?: run {
                actorExtra.isVisible = false
            }

            if (card.voiceActor == null) {
                voiceActorImageHolder.isVisible = false
                voiceActorName.isVisible = false
            } else {
                voiceActorName.text = card.voiceActor.name
                voiceActorImageHolder.isVisible = voiceActorImage.setImage(card.voiceActor.image)
            }
        }
    }
}

class ActorDiffCallback(
    private val oldList: List<ActorData>,
    private val newList: List<ActorData>
) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].actor.name == newList[newItemPosition].actor.name

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}