// https://github.com/googlecodelabs/android-kotlin-animation-property-animation/tree/master/begin

package com.lagradost.cloudstream3.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.R
import kotlinx.android.synthetic.main.activity_easter_egg_monke.*
import java.util.*

class EasterEggMonke : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_easter_egg_monke)

        val handler = Handler(mainLooper)
        lateinit var runnable: Runnable
        runnable = Runnable {
            shower()
            handler.postDelayed(runnable, 300)
        }
        handler.postDelayed(runnable, 1000)

    }

    private fun shower() {

        val containerW = frame.width
        val containerH = frame.height
        var starW: Float = monke.width.toFloat()
        var starH: Float = monke.height.toFloat()

        val newStar = AppCompatImageView(this)
        val idx = (monkeys.size * Math.random()).toInt()
        newStar.setImageResource(monkeys[idx])
        newStar.isVisible = true
        newStar.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT)
        frame.addView(newStar)

        newStar.scaleX = Math.random().toFloat() * 1.5f +  newStar.scaleX
        newStar.scaleY = newStar.scaleX
        starW *= newStar.scaleX
        starH *= newStar.scaleY

        newStar.translationX = Math.random().toFloat() * containerW - starW / 2

        val mover = ObjectAnimator.ofFloat(newStar, View.TRANSLATION_Y, -starH, containerH + starH)
        mover.interpolator = AccelerateInterpolator(1f)

        val rotator = ObjectAnimator.ofFloat(newStar, View.ROTATION,
            (Math.random() * 1080).toFloat())
        rotator.interpolator = LinearInterpolator()

        val set = AnimatorSet()
        set.playTogether(mover, rotator)
        set.duration = (Math.random() * 1500 + 2500).toLong()

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                frame.removeView(newStar)
            }
        })

        set.start()
    }

    companion object {
        val monkeys = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
            R.drawable.benene,
            R.drawable.ic_launcher_foreground
        )
    }
}