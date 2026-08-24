package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixDimens
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

class CloneflixInputField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.drawable.cloneflix_bg_input_field)
        setTextColor(ContextCompat.getColor(context, R.color.cloneflix_primary_white))
        setHintTextColor(ContextCompat.getColor(context, R.color.cloneflix_grey_200))
        typeface = CloneflixTypography.getFontTypeface(context, CloneflixTypography.Weight.REGULAR)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.cloneflix_text_regular_body))

        val paddingH = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_l)
        val paddingV = resources.getDimensionPixelSize(R.dimen.cloneflix_spacing_m)
        setPadding(paddingH, paddingV, paddingH, paddingV)
        minHeight = resources.getDimensionPixelSize(R.dimen.cloneflix_input_height)
    }
}
