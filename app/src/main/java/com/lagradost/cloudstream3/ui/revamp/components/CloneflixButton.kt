package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixDimens
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

class CloneflixButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    enum class Variant {
        PRIMARY,
        SECONDARY,
        OUTLINE,
        GHOST
    }

    enum class Size {
        LARGE,
        MEDIUM,
        SMALL
    }

    private var currentVariant: Variant = Variant.PRIMARY
    private var currentSize: Size = Size.MEDIUM

    init {
        isAllCaps = false
        typeface = CloneflixTypography.getFontTypeface(context, CloneflixTypography.Weight.BOLD)

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CloneflixButton)
            val variantIndex = typedArray.getInt(R.styleable.CloneflixButton_cloneflixButtonVariant, 0)
            val sizeIndex = typedArray.getInt(R.styleable.CloneflixButton_cloneflixButtonSize, 1)
            val iconRes = typedArray.getResourceId(R.styleable.CloneflixButton_cloneflixIcon, 0)
            val btnText = typedArray.getString(R.styleable.CloneflixButton_cloneflixText)
            typedArray.recycle()

            currentVariant = when (variantIndex) {
                1 -> Variant.SECONDARY
                2 -> Variant.OUTLINE
                3 -> Variant.GHOST
                else -> Variant.PRIMARY
            }

            currentSize = when (sizeIndex) {
                0 -> Size.LARGE
                2 -> Size.SMALL
                else -> Size.MEDIUM
            }

            if (iconRes != 0) {
                setIconResource(iconRes)
            }
            if (btnText != null) {
                text = btnText
            }
        }

        applyVariant(currentVariant)
        applySize(currentSize)
    }

    fun setVariant(variant: Variant) {
        currentVariant = variant
        applyVariant(variant)
    }

    fun setButtonSize(size: Size) {
        currentSize = size
        applySize(size)
    }

    private fun applyVariant(variant: Variant) {
        when (variant) {
            Variant.PRIMARY -> {
                setBackgroundResource(R.drawable.cloneflix_btn_primary)
                setTextColor(ContextCompat.getColor(context, R.color.cloneflix_primary_white))
                iconTint = ContextCompat.getColorStateList(context, R.color.cloneflix_primary_white)
                strokeWidth = 0
            }
            Variant.SECONDARY -> {
                setBackgroundResource(R.drawable.cloneflix_btn_secondary)
                setTextColor(ContextCompat.getColor(context, R.color.cloneflix_primary_black))
                iconTint = ContextCompat.getColorStateList(context, R.color.cloneflix_primary_black)
                strokeWidth = 0
            }
            Variant.OUTLINE -> {
                setBackgroundResource(R.drawable.cloneflix_btn_outline)
                setTextColor(ContextCompat.getColor(context, R.color.cloneflix_primary_white))
                iconTint = ContextCompat.getColorStateList(context, R.color.cloneflix_primary_white)
            }
            Variant.GHOST -> {
                setBackgroundResource(R.drawable.cloneflix_btn_ghost)
                setTextColor(ContextCompat.getColor(context, R.color.cloneflix_primary_white))
                iconTint = ContextCompat.getColorStateList(context, R.color.cloneflix_primary_white)
                strokeWidth = 0
            }
        }
    }

    private fun applySize(size: Size) {
        when (size) {
            Size.LARGE -> {
                minHeight = resources.getDimensionPixelSize(R.dimen.cloneflix_button_height_large)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.cloneflix_text_regular_headline1))
                val padH = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_2xl)
                val padV = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_m)
                setPadding(padH, padV, padH, padV)
            }
            Size.MEDIUM -> {
                minHeight = resources.getDimensionPixelSize(R.dimen.cloneflix_button_height_medium)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.cloneflix_text_regular_body))
                val padH = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_l)
                val padV = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_s)
                setPadding(padH, padV, padH, padV)
            }
            Size.SMALL -> {
                minHeight = resources.getDimensionPixelSize(R.dimen.cloneflix_button_height_small)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.cloneflix_text_regular_smallbody))
                val padH = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_m)
                val padV = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_xs)
                setPadding(padH, padV, padH, padV)
            }
        }
    }
}
