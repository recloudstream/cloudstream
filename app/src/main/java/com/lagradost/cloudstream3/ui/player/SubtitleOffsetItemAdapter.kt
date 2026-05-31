package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isInvisible
import com.lagradost.cloudstream3.databinding.SubtitleOffsetItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import kotlin.math.roundToInt

data class SubtitleCue(val startTimeMs: Long, val durationMs: Long, val text: List<String>) {
    val endTimeMs = startTimeMs + durationMs
}

class SubtitleOffsetItemAdapter(
    private var currentTimeMs: Long,
    val clickCallback: (SubtitleCue) -> Unit
) :
    NoStateAdapter<SubtitleCue>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        a.startTimeMs == b.startTimeMs
    })) {

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = SubtitleOffsetItemBinding.inflate(inflater, parent, false)
        return ViewHolderState(binding)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SubtitleCue, position: Int) {
        val binding = holder.view as? SubtitleOffsetItemBinding ?: return

        binding.root.setOnClickListener {
            clickCallback.invoke(item)
        }

        binding.subtitleText.text = item.text.joinToString("\n")

        val timeMs = currentTimeMs
        val startTime = item.startTimeMs
        val endTime = item.endTimeMs

        val newAlpha = if (timeMs >= startTime) 1f else 0.5f
        ObjectAnimator.ofFloat(
            binding.subtitleText,
            "alpha",
            binding.subtitleText.alpha,
            newAlpha
        ).apply {
            interpolator = DecelerateInterpolator()
        }.start()

        val showProgress = timeMs in startTime..<endTime
        // Invisible to prevent layout changes
        binding.subtitleProgress.isInvisible = !showProgress

        if (showProgress) {
            // Set progress to currentTime/remainingTime
            // Multiply by 1000 since the max is 1000
            val progressValue = ((timeMs - startTime) * 1000f / item.durationMs).roundToInt()

            // Animate the progress change
            ObjectAnimator.ofInt(
                binding.subtitleProgress,
                "progress",
                binding.subtitleProgress.progress,
                progressValue
            ).apply {
                interpolator = DecelerateInterpolator()
            }.start()
        } else {
            // Reset progress when not visible
            binding.subtitleProgress.progress = 0
        }
    }

    fun getLatestActiveItem(position: Long): Int {
        return immutableCurrentList.withIndex().lastOrNull {
            position >= it.value.startTimeMs
        }?.index ?: 0
    }

    fun updateTime(timeMs: Long) {
        val previousTime = currentTimeMs
        currentTimeMs = timeMs

        val earlyTime = minOf(previousTime, timeMs)
        val lateTime = maxOf(previousTime, timeMs)

        // TODO Add binary search and notifyItemRangeChanged
        val affectedItems = immutableCurrentList.withIndex().filter { cue ->
            // Padding is required in the range because changes can be done within one single subtitle range,
            // and that subtitle needs to be updated
            cue.value.startTimeMs in (earlyTime - cue.value.durationMs)..(lateTime + cue.value.durationMs)
        }

        affectedItems.forEach { item ->
            // This could likely be a range
            this.notifyItemChanged(item.index)
        }
    }
}