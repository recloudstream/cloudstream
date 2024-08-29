package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.SubtitleOffsetItemBinding
import com.lagradost.cloudstream3.utils.AppContextUtils
import kotlin.math.roundToInt

data class SubtitleCue(val startTimeMs: Long, val durationMs: Long, val text: List<String>) {
    val endTimeMs = startTimeMs + durationMs
}

class SubtitleOffsetItemAdapter(
    private var currentTimeMs: Long,
    override val items: MutableList<SubtitleCue>,
    val clickCallback: (SubtitleCue) -> Unit
) :
    AppContextUtils.DiffAdapter<SubtitleCue>(items) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = SubtitleOffsetItemBinding.inflate(inflater, parent, false)
        return SubtitleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SubtitleViewHolder -> holder.bind(items[position])
        }
    }

    fun getLatestActiveItem(position: Long): Int {
        return items.withIndex().lastOrNull {
            position >= it.value.startTimeMs
        }?.index ?: 0
    }

    fun updateTime(timeMs: Long) {
        val previousTime = currentTimeMs
        currentTimeMs = timeMs

        val earlyTime = minOf(previousTime, timeMs)
        val lateTime = maxOf(previousTime, timeMs)
        val affectedItems = items.withIndex().filter { cue ->
            // Padding is required in the range because changes can be done within one single subtitle range,
            // and that subtitle needs to be updated
            cue.value.startTimeMs in (earlyTime - cue.value.durationMs)..(lateTime + cue.value.durationMs)
        }

        affectedItems.forEach { item ->
            // This could likely be a range
            this.notifyItemChanged(item.index)
        }
    }

    private inner class SubtitleViewHolder(
        val binding: SubtitleOffsetItemBinding,
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            data: SubtitleCue
        ) {
            binding.root.setOnClickListener {
                clickCallback.invoke(data)
            }

            binding.subtitleText.text = data.text.joinToString("\n")

            val timeMs = currentTimeMs
            val startTime = data.startTimeMs
            val endTime = data.endTimeMs

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
                val progressValue = ((timeMs - startTime) * 1000f / data.durationMs).roundToInt()

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
    }
}