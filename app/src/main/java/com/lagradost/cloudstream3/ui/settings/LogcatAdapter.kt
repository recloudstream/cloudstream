package com.lagradost.cloudstream3.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ItemLogcatBinding

class LogcatAdapter(
    private val logs: List<String>
) : RecyclerView.Adapter<LogcatAdapter.LogViewHolder>() {

    inner class LogViewHolder(
        val binding: ItemLogcatBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogcatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.binding.logText.text = logs[position]
    }

    override fun getItemCount(): Int = logs.count()
}