package com.lagradost.cloudstream3.ui.player.source_priority

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.android.synthetic.main.player_quality_profile_item.view.*

class ProfilesAdapter(
    override val items: MutableList<QualityDataHelper.QualityProfile>,
    val usedProfile: Int,
    val clickCallback: (oldIndex: Int?, newIndex: Int) -> Unit,
) :
    AppUtils.DiffAdapter<QualityDataHelper.QualityProfile>(
        items,
        comparison = { first: QualityDataHelper.QualityProfile, second: QualityDataHelper.QualityProfile ->
            first.id == second.id
        }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ProfilesViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.player_quality_profile_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProfilesViewHolder -> holder.bind(items[position], position)
        }
    }

    private var currentItem: Pair<Int, QualityDataHelper.QualityProfile>? = null

    fun getCurrentProfile(): QualityDataHelper.QualityProfile? {
        return currentItem?.second
    }

    inner class ProfilesViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: QualityDataHelper.QualityProfile, index: Int) {
            val priorityText: TextView = itemView.profile_text
            val wifiText: TextView = itemView.text_is_wifi
            val dataText: TextView = itemView.text_is_mobile_data
            val outline: View = itemView.outline

            priorityText.text = item.name.asString(itemView.context)
            dataText.isVisible = item.type == QualityDataHelper.QualityProfileType.Data
            wifiText.isVisible = item.type == QualityDataHelper.QualityProfileType.WiFi

            fun setCurrentItem() {
                val prevIndex = currentItem?.first
                currentItem = index to item
                clickCallback.invoke(prevIndex, index)
            }
            outline.isVisible = currentItem?.second?.id == item.id


            val textStyle =
                if (item.id == usedProfile) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                }

            priorityText.setTypeface(null, textStyle)

            itemView.setOnClickListener {
                setCurrentItem()
            }
        }
    }
}