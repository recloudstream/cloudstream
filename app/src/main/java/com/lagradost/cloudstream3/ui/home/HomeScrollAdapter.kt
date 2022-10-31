package com.lagradost.cloudstream3.ui.home

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.utils.UIHelper.setImage


class HomeScrollAdapter(private val onPrimaryCallback: (LoadResponse) -> Unit) : PagerAdapter() {
    private var items: List<LoadResponse> = listOf()

    fun setItems(newItems: List<LoadResponse>) {
        items = newItems

        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return Int.MAX_VALUE//items.size
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE//super.getItemPosition(`object`)
    }

    private fun getItemAtPosition(idx: Int): LoadResponse {
        return items[idx % items.size]
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        super.setPrimaryItem(container, position, `object`)
        onPrimaryCallback.invoke(getItemAtPosition(position))
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val image = ImageView(container.context)
        val item = getItemAtPosition(position)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setImage(item.posterUrl ?: item.backgroundPosterUrl, item.posterHeaders)

        // val itemView: View = mLayoutInflater.inflate(R.layout.pager_item, container, false)

        // val imageView: ImageView = itemView.findViewById<View>(R.id.imageView) as ImageView
        // imageView.setImageResource(mResources.get(position))

        container.addView(image)

        return image
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }
}