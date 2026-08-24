package com.lagradost.cloudstream3.ui.revamp.typography

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.RevampItemTypographyStyleBinding
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTheme
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

class CloneflixTypographyAdapter(
    private val styles: List<CloneflixTypography.StyleLevel>,
    private val sampleTextProvider: ((CloneflixTypography.StyleLevel) -> String)? = null
) : RecyclerView.Adapter<CloneflixTypographyAdapter.ViewHolder>() {

    class ViewHolder(val binding: RevampItemTypographyStyleBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RevampItemTypographyStyleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val style = styles[position]
        holder.binding.styleLabel.text = style.label
        holder.binding.styleMetrics.text = "Size: ${style.sizeSp.toInt()}sp • LH: ${style.lineHeightSp.toInt()}sp"

        val sample = sampleTextProvider?.invoke(style) ?: when (style) {
            CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE,
            CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE -> "Unlimited movies, TV shows, and more"
            CloneflixTypography.StyleLevel.LOGO_BEBAS -> "CLONEFLIX"
            else -> "Watch anywhere. Cancel anytime."
        }

        holder.binding.styleSampleText.text = sample
        TextViewCompat.setTextAppearance(holder.binding.styleSampleText, style.styleResId)
        holder.binding.styleSampleText.typeface =
            CloneflixTypography.getFontTypeface(holder.itemView.context, style.weight)

        holder.itemView.setOnClickListener {
            CloneflixTheme.copyToClipboard(
                holder.itemView.context,
                style.label,
                "fontSize: ${style.sizeSp}sp, lineHeight: ${style.lineHeightSp}sp"
            )
        }
    }

    override fun getItemCount(): Int = styles.size
}
