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
import androidx.palette.graphics.Palette
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.PlayerQualityProfileItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.drawableToBitmap
import com.lagradost.cloudstream3.utils.setText

class ProfilesAdapter(
    val usedProfile: Int?,
    val clickCallback: (oldIndex: Int?, newIndex: Int) -> Unit,
) :
    NoStateAdapter<QualityDataHelper.QualityProfile>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        a.id == b.id
    })) {

    companion object {
        private val art = arrayOf(
            R.drawable.profile_bg_teal,
            R.drawable.profile_bg_blue,
            R.drawable.profile_bg_dark_blue,
            R.drawable.profile_bg_purple,
            R.drawable.profile_bg_pink,
            R.drawable.profile_bg_red,
            R.drawable.profile_bg_orange,
        )
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            PlayerQualityProfileItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is PlayerQualityProfileItemBinding -> {
                clearImage(binding.profileImageBackground)
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: QualityDataHelper.QualityProfile,
        position: Int
    ) {
        val binding = holder.view as? PlayerQualityProfileItemBinding ?: return

        val priorityText: TextView = binding.profileText
        val profileBg: ImageView = binding.profileImageBackground
        val wifiText: TextView = binding.textIsWifi
        val dataText: TextView = binding.textIsMobileData
        val downloadText: TextView = binding.textIsDownloadData
        val outline: View = binding.outline
        val cardView: View = binding.cardView
        val itemView = holder.itemView

        priorityText.setText(item.name)
        dataText.isVisible = item.types.contains(QualityDataHelper.QualityProfileType.Data)
        wifiText.isVisible = item.types.contains(QualityDataHelper.QualityProfileType.WiFi)
        downloadText.isVisible = item.types.contains(QualityDataHelper.QualityProfileType.Download)

        fun setCurrentItem() {
            val prevIndex = currentItem
            // Prevent UI bug when re-selecting the item quickly
            if (prevIndex == position) {
                return
            }
            currentItem = position
            clickCallback.invoke(prevIndex, position)
        }

        outline.isVisible = currentItem == position
        val drawableResId = art[position % art.size]
        profileBg.loadImage(drawableResId)

        val drawable = ContextCompat.getDrawable(itemView.context, drawableResId)
        if (drawable != null) {
            // Convert Drawable to Bitmap
            val bitmap = drawableToBitmap(drawable)
            if (bitmap != null) {
                // Use Palette to extract colors from the bitmap
                Palette.from(bitmap).generate { palette ->
                    val color = palette?.getDarkVibrantColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.dubColorBg
                        )
                    )

                    if (color != null) {
                        wifiText.backgroundTintList = ColorStateList.valueOf(color)
                        dataText.backgroundTintList = ColorStateList.valueOf(color)
                        downloadText.backgroundTintList = ColorStateList.valueOf(color)
                    }
                }
            }
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

    private var currentItem: Int? = null

    fun getCurrentProfile(): QualityDataHelper.QualityProfile? {
        return currentItem?.let { index -> immutableCurrentList.getOrNull(index) }
    }
}