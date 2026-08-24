package com.lagradost.cloudstream3.ui.revamp.colors

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.RevampItemColorSectionBinding
import com.lagradost.cloudstream3.ui.revamp.components.CloneflixColorSwatchView
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors

class CloneflixColorsAdapter(
    private val sections: Map<String, List<CloneflixColors.ColorToken>>
) : RecyclerView.Adapter<CloneflixColorsAdapter.ViewHolder>() {

    private val sectionList = sections.toList()

    class ViewHolder(val binding: RevampItemColorSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RevampItemColorSectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (categoryName, tokens) = sectionList[position]
        holder.binding.sectionTitle.text = categoryName.uppercase()
        holder.binding.swatchesContainer.removeAllViews()

        val context = holder.itemView.context
        for (token in tokens) {
            val swatchView = CloneflixColorSwatchView(context).apply {
                bind(token)
            }
            holder.binding.swatchesContainer.addView(swatchView)
        }
    }

    override fun getItemCount(): Int = sectionList.size
}
