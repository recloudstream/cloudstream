package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/*
class ImageAdapter(context: Context, val resource: Int) : ArrayAdapter<Int>(context, resource) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val newConvertView = convertView ?: run {
            val mInflater = context
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mInflater.inflate(resource, null)
        }
        getItem(position)?.let { (newConvertView as? ImageView?)?.setImageResource(it) }
        return newConvertView
    }
}*/

class ImageAdapter(
    val layout: Int,
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val images: MutableList<Int> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ImageViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> {
                holder.bind(images[position])
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

    class ImageViewHolder
    constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        fun bind(img: Int) {
            (itemView as? ImageView?)?.setImageResource(img)
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