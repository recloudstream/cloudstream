package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ResultMiniImageBinding
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

const val IMAGE_CLICK = 0
const val IMAGE_LONG_CLICK = 1

class ImageAdapter(
    val clickCallback: ((Int) -> Unit)? = null,
    val nextFocusUp: Int? = null,
    val nextFocusDown: Int? = null,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val images: MutableList<Int> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ImageViewHolder(
            //result_mini_image
            ResultMiniImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
           // LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> {
                holder.bind(images[position], clickCallback, nextFocusUp, nextFocusDown)
            }
        }
    }

    override fun getItemCount(): Int {
        return images.size
    }

    override fun getItemId(position: Int): Long {
        return images[position].toLong()
    }

    fun updateList(newList: List<Int>) {
        val diffResult = DiffUtil.calculateDiff(
            DiffCallback(this.images, newList)
        )

        images.clear()
        images.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
    }

    class ImageViewHolder(val binding: ResultMiniImageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            img: Int,
            clickCallback: ((Int) -> Unit)?,
            nextFocusUp: Int?,
            nextFocusDown: Int?,
        ) {
            binding.root.apply {
                setImageResource(img)
                if (nextFocusDown != null) {
                    this.nextFocusDownId = nextFocusDown
                }
                if (nextFocusUp != null) {
                    this.nextFocusUpId = nextFocusUp
                }
                if (clickCallback != null) {
                    if (isLayout(TV)) {
                        isClickable = true
                        isLongClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }
                    setOnClickListener {
                        clickCallback.invoke(IMAGE_CLICK)
                    }
                    setOnLongClickListener {
                        clickCallback.invoke(IMAGE_LONG_CLICK)
                        return@setOnLongClickListener true
                    }
                }
            }
        }
    }
}

class DiffCallback<T>(private val oldList: List<T>, private val newList: List<T>) :
    DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}