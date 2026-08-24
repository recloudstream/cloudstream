package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RevampViewHeaderBinding

class CloneflixHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding: RevampViewHeaderBinding =
        RevampViewHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CloneflixHeaderView)
            val title = typedArray.getString(R.styleable.CloneflixHeaderView_headerTitle)
            val subtitle = typedArray.getString(R.styleable.CloneflixHeaderView_headerSubtitle)
            val iconRes = typedArray.getResourceId(R.styleable.CloneflixHeaderView_headerIcon, 0)
            val showDivider = typedArray.getBoolean(R.styleable.CloneflixHeaderView_showDivider, true)
            typedArray.recycle()

            title?.let { setTitle(it) }
            subtitle?.let { setSubtitle(it) }
            if (iconRes != 0) {
                setIcon(iconRes)
            }
            setDividerVisible(showDivider)
        }
    }

    fun setTitle(title: CharSequence) {
        binding.headerTitle.text = title
    }

    fun setSubtitle(subtitle: CharSequence?) {
        if (!subtitle.isNullOrBlank()) {
            binding.headerSubtitle.text = subtitle
            binding.headerSubtitle.visibility = VISIBLE
        } else {
            binding.headerSubtitle.visibility = GONE
        }
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        binding.headerIcon.setImageResource(iconRes)
    }

    fun setDividerVisible(visible: Boolean) {
        binding.headerDivider.visibility = if (visible) VISIBLE else GONE
    }
}
