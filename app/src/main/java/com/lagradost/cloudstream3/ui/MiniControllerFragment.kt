package com.lagradost.cloudstream3.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import com.google.android.gms.cast.framework.media.widget.MiniControllerFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.adjustAlpha
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.toPx


class MyMiniControllerFragment : MiniControllerFragment() {
    var currentColor: Int = 0

    // I KNOW, KINDA SPAGHETTI SOLUTION, BUT IT WORKS
    override fun onInflate(context: Context, attributeSet: AttributeSet, bundle: Bundle?) {
        val obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.CustomCast, 0, 0)
        if (obtainStyledAttributes.hasValue(R.styleable.CustomCast_customCastBackgroundColor)) {
            currentColor = obtainStyledAttributes.getColor(R.styleable.CustomCast_customCastBackgroundColor, 0)
        }
        obtainStyledAttributes.recycle()
        super.onInflate(context, attributeSet, bundle)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // SEE https://github.com/dandar3/android-google-play-services-cast-framework/blob/master/res/layout/cast_mini_controller.xml
        try {
            val progressBar: ProgressBar? = view.findViewById(R.id.progressBar)
            val containerAll: LinearLayout? = view.findViewById(R.id.container_all)
            val containerCurrent: RelativeLayout? = view.findViewById(R.id.container_current)

            context?.let { ctx ->
                progressBar?.setBackgroundColor(adjustAlpha(ctx.colorFromAttribute(R.attr.colorPrimary), 0.35f))
                val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 2.toPx)

                progressBar?.layoutParams = params

                if (currentColor != 0) {
                    containerCurrent?.setBackgroundColor(currentColor)
                }
            }
            val child = containerAll?.getChildAt(0)
            child?.alpha = 0f // REMOVE GRADIENT

        } catch (e: Exception) {
            // JUST IN CASE
        }
    }
}
