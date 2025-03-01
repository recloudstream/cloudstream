package com.lagradost.cloudstream3.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentEasterEggMonkeBinding
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class EasterEggMonkeFragment : Fragment() {

    private var _binding: FragmentEasterEggMonkeBinding? = null
    private val binding get() = _binding!!

    // planet of monks
    private val monkeys: List<Int> = listOf(
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
        R.drawable.ic_launcher_foreground,
        R.drawable.quick_novel_icon,
    )

    private val activeMonkeys = mutableListOf<ImageView>()
    private var spawningJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEasterEggMonkeBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.hideSystemUI()
        spawningJob = lifecycleScope.launch {
            delay(1000)
            while (isActive) {
                spawnMonkey()
                delay(500)
            }
        }
    }

    private fun spawnMonkey() {
        val newMonkey = ImageView(context ?: return).apply {
            setImageResource(monkeys.random())
            isVisible = true
        }

        val initialScale = Random.nextFloat() * 1.5f + 0.5f
        newMonkey.scaleX = initialScale
        newMonkey.scaleY = initialScale

        newMonkey.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val monkeyW = newMonkey.measuredWidth * initialScale
        val monkeyH = newMonkey.measuredHeight * initialScale

        newMonkey.x = Random.nextFloat() * (binding.frame.width.toFloat() - monkeyW)
        newMonkey.y = Random.nextFloat() * (binding.frame.height.toFloat() - monkeyH)

        binding.frame.addView(newMonkey, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        activeMonkeys.add(newMonkey)

        newMonkey.alpha = 0f
        ObjectAnimator.ofFloat(newMonkey, View.ALPHA, 0f, 1f).apply {
            duration = Random.nextLong(1000, 2500)
            interpolator = AccelerateInterpolator()
            start()
        }

        @SuppressLint("ClickableViewAccessibility")
        newMonkey.setOnTouchListener { view, event -> handleTouch(view, event) }

        startFloatingAnimation(newMonkey)
    }

    private fun startFloatingAnimation(monkey: ImageView) {
        val floatUpAnimator = ObjectAnimator.ofFloat(
            monkey, View.TRANSLATION_Y, monkey.y, -monkey.height.toFloat()
        ).apply {
            duration = Random.nextLong(8000, 15000)
            interpolator = LinearInterpolator()
        }

        floatUpAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // necessary check because binding becomes null but monkes are still moving until onDestroy()
                if (_binding != null) {
                    binding.frame.removeView(monkey)
                    activeMonkeys.remove(monkey)
                }
            }
        })

        floatUpAnimator.start()
        monkey.tag = floatUpAnimator
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val monkey = view as ImageView
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                (monkey.tag as? ObjectAnimator)?.pause()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Update both X and Y positions properly
                monkey.x = event.rawX - monkey.width / 2
                monkey.y = event.rawY - monkey.height / 2

                // Check if monkey touches the screen edge
                if (isTouchingEdge(monkey)) {
                    removeMonkey(monkey)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTouchingEdge(monkey)) {
                    removeMonkey(monkey)
                } else {
                    startFloatingAnimation(monkey)
                }
                return true
            }
        }
        return false
    }

    private fun isTouchingEdge(monkey: ImageView): Boolean {
        return monkey.x <= 0 || monkey.x + monkey.width >= binding.frame.width ||
                monkey.y <= 0 || monkey.y + monkey.height >= binding.frame.height
    }

    private fun removeMonkey(monkey: ImageView) {
        // Fade out and remove the monkey
        ObjectAnimator.ofFloat(monkey, View.ALPHA, 1f, 0f).apply {
            duration = 300
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.frame.removeView(monkey)
                    activeMonkeys.remove(monkey)
                }
            })
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.showSystemUI()
        spawningJob?.cancel()
        _binding = null
    }
}