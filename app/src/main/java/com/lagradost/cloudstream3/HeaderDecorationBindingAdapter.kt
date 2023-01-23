package com.lagradost.cloudstream3

import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.ui.HeaderViewDecoration

fun setHeaderDecoration(view: RecyclerView, @LayoutRes headerViewRes: Int) {
    val headerView = LayoutInflater.from(view.context).inflate(headerViewRes, null)
    view.addItemDecoration(HeaderViewDecoration(headerView))
}