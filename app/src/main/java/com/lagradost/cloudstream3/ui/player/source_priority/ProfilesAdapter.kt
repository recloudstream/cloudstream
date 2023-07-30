package com.lagradost.cloudstream3.ui.player.source_priority

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerQualityProfileItemBinding
import com.lagradost.cloudstream3.ui.result.UiImage
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.UIHelper.setImage

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
            PlayerQualityProfileItemBinding.inflate(LayoutInflater.from(parent.context),parent,false)
            //LayoutInflater.from(parent.context)
            //    .inflate(R.layout.player_quality_profile_item, parent, false)
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
        val binding: PlayerQualityProfileItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val art = listOf(
            R.drawable.profile_bg_teal,
            R.drawable.profile_bg_blue,
            R.drawable.profile_bg_dark_blue,
            R.drawable.profile_bg_purple,
            R.drawable.profile_bg_pink,
            R.drawable.profile_bg_red,
            R.drawable.profile_bg_orange,
        )

        fun bind(item: QualityDataHelper.QualityProfile, index: Int) {
            val priorityText: TextView = binding.profileText
            val profileBg: ImageView = binding.profileImageBackground
            val wifiText: TextView = binding.textIsWifi
            val dataText: TextView = binding.textIsMobileData
            val outline: View = binding.outline
            val cardView: View = binding.cardView

            priorityText.text = item.name.asString(itemView.context)
            dataText.isVisible = item.type == QualityDataHelper.QualityProfileType.Data
            wifiText.isVisible = item.type == QualityDataHelper.QualityProfileType.WiFi

            fun setCurrentItem() {
                val prevIndex = currentItem?.first
                // Prevent UI bug when re-selecting the item quickly
                if (prevIndex == index) {
                    return
                }
                currentItem = index to item
                clickCallback.invoke(prevIndex, index)
            }

            outline.isVisible = currentItem?.second?.id == item.id

            profileBg.setImage(UiImage.Drawable(art[index % art.size]), null, false) { palette ->
                val color = palette.getDarkVibrantColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.dubColorBg
                    )
                )
                wifiText.backgroundTintList = ColorStateList.valueOf(color)
                dataText.backgroundTintList = ColorStateList.valueOf(color)
            }

            val textStyle =
                if (item.id == usedProfile) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                }

            priorityText.setTypeface(null, textStyle)

            cardView.setOnClickListener {
                setCurrentItem()
            }
        }
    }
}