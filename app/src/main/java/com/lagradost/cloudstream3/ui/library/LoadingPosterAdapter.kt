package com.lagradost.cloudstream3.ui.library

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.lagradost.cloudstream3.R

class LoadingPosterAdapter(context: Context, private val itemCount: Int) :
    BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return itemCount
    }

    override fun getItem(position: Int): Any? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return convertView ?: inflater.inflate(R.layout.loading_poster_dynamic, parent, false)
    }
}