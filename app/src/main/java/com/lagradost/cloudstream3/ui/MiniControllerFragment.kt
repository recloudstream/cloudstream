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
import java.lang.ref.WeakReference


class MyMiniControllerFragment : MiniControllerFragment() {
    var currentColor: Int = 0

    override fun onDestroy() {
        currentColor = 0
        super.onDestroy()
    }

    // I KNOW, KINDA SPAGHETTI SOLUTION, BUT IT WORKS
    override fun onInflate(context: Context, attributeSet: AttributeSet, bundle: Bundle?) {
        super.onInflate(context, attributeSet, bundle)

        // somehow this leaks and I really dont know why, it seams like if you go back to a fragment with this, it leaks????
        if (currentColor == 0) {
            WeakReference(
                context.obtainStyledAttributes(
                    attributeSet,
                    R.styleable.CustomCast
                )
            ).apply {
                if (get()
                        ?.hasValue(R.styleable.CustomCast_customCastBackgroundColor) == true
                ) {
                    currentColor =
                        get()
                            ?.getColor(R.styleable.CustomCast_customCastBackgroundColor, 0) ?: 0
                }
                get()?.recycle()
            }.clear()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // SEE https://github.com/dandar3/android-google-play-services-cast-framework/blob/master/res/layout/cast_mini_controller.xml
        try {
            val progressBar: ProgressBar? = view.findViewById(R.id.progressBar)
            val containerAll: LinearLayout? = view.findViewById(R.id.container_all)
            val containerCurrent: RelativeLayout? = view.findViewById(R.id.container_current)

            context?.let { ctx ->
                progressBar?.setBackgroundColor(
                    adjustAlpha(
                        ctx.colorFromAttribute(R.attr.colorPrimary),
                        0.35f
                    )
                )
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    2.toPx
                )

                progressBar?.layoutParams = params
                val color = currentColor
                if (color != 0)
                    containerCurrent?.setBackgroundColor(color)
            }
            val child = containerAll?.getChildAt(0)
            child?.alpha = 0f // REMOVE GRADIENT
        } catch (e: Exception) {
            // JUST IN CASE
        }
    }
}
