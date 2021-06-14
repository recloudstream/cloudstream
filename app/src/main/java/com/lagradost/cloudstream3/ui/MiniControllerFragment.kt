package com.lagradost.cloudstream3.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import com.google.android.gms.cast.framework.media.widget.MiniControllerFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.adjustAlpha
import com.lagradost.cloudstream3.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.UIHelper.toPx

class MyMiniControllerFragment : MiniControllerFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // SEE https://github.com/dandar3/android-google-play-services-cast-framework/blob/master/res/layout/cast_mini_controller.xml
        try {
            val progressBar: ProgressBar? = view.findViewById(R.id.progressBar)
            val containerAll: LinearLayout? = view.findViewById(R.id.container_all)

            context?.let { ctx ->
                progressBar?.setBackgroundColor(adjustAlpha(ctx.colorFromAttribute(R.attr.colorPrimary), 0.35f))
                val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 2.toPx)

                progressBar?.layoutParams = params
            }
            val child = containerAll?.getChildAt(0)
            child?.alpha = 0f // REMOVE GRADIENT

        } catch (e: Exception) {
            // JUST IN CASE
        }
    }
}
