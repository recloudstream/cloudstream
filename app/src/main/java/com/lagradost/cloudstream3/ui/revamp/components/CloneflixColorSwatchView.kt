package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.RevampItemColorSwatchBinding
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixColors
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixDimens
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTheme

class CloneflixColorSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: RevampItemColorSwatchBinding =
        RevampItemColorSwatchBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentToken: CloneflixColors.ColorToken? = null

    fun bind(token: CloneflixColors.ColorToken) {
        currentToken = token
        binding.colorName.text = token.name
        binding.colorHexAndRgb.text = "${token.hex}  •  ${token.rgbDescription}"

        val previewDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimension(R.dimen.cloneflix_radius_s)
            setColor(token.colorInt)
            setStroke(
                resources.getDimensionPixelSize(R.dimen.cloneflix_divider_height),
                ContextCompat.getColor(context, R.color.cloneflix_border_subtle)
            )
        }
        binding.colorPreview.background = previewDrawable

        setOnClickListener {
            CloneflixTheme.copyToClipboard(context, token.name, token.hex)
            binding.copyButton.setImageResource(R.drawable.cloneflix_ic_check)
            postDelayed({
                binding.copyButton.setImageResource(R.drawable.cloneflix_ic_copy)
            }, 1200)
        }
    }
}
