package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isInvisible
import com.lagradost.cloudstream3.databinding.DialogDualSubtitlesItemBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DualSubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val primaryText: String?,
    val secondaryText: String?,
)

object DualSubtitleAligner {
    fun align(
        primaryCues: List<SubtitleCue>,
        primaryOffset: Long,
        secondaryCues: List<SubtitleCue>,
        secondaryOffset: Long
    ): List<DualSubtitleCue> {
        val pList = primaryCues.map {
            SubtitleCue(it.startTimeMs - primaryOffset, it.durationMs, it.text)
        }.sortedBy { it.startTimeMs }

        val sList = secondaryCues.map {
            SubtitleCue(it.startTimeMs - secondaryOffset, it.durationMs, it.text)
        }.sortedBy { it.startTimeMs }

        if (pList.isEmpty() && sList.isEmpty()) return emptyList()
        if (pList.isEmpty()) {
            return sList.map { DualSubtitleCue(it.startTimeMs, it.endTimeMs, null, it.text.joinToString("\n")) }
        }
        if (sList.isEmpty()) {
            return pList.map { DualSubtitleCue(it.startTimeMs, it.endTimeMs, it.text.joinToString("\n"), null) }
        }

        val result = mutableListOf<DualSubtitleCue>()
        var pIdx = 0
        var sIdx = 0

        while (pIdx < pList.size && sIdx < sList.size) {
            val p = pList[pIdx]
            val s = sList[sIdx]

            val overlaps = (p.startTimeMs < s.endTimeMs && s.startTimeMs < p.endTimeMs) ||
                    kotlin.math.abs(p.startTimeMs - s.startTimeMs) <= 1200L

            if (overlaps) {
                result.add(
                    DualSubtitleCue(
                        startTimeMs = min(p.startTimeMs, s.startTimeMs),
                        endTimeMs = max(p.endTimeMs, s.endTimeMs),
                        primaryText = p.text.joinToString("\n"),
                        secondaryText = s.text.joinToString("\n")
                    )
                )
                pIdx++
                sIdx++
            } else if (p.startTimeMs < s.startTimeMs) {
                result.add(
                    DualSubtitleCue(
                        startTimeMs = p.startTimeMs,
                        endTimeMs = p.endTimeMs,
                        primaryText = p.text.joinToString("\n"),
                        secondaryText = null
                    )
                )
                pIdx++
            } else {
                result.add(
                    DualSubtitleCue(
                        startTimeMs = s.startTimeMs,
                        endTimeMs = s.endTimeMs,
                        primaryText = null,
                        secondaryText = s.text.joinToString("\n")
                    )
                )
                sIdx++
            }
        }

        while (pIdx < pList.size) {
            val p = pList[pIdx++]
            result.add(DualSubtitleCue(p.startTimeMs, p.endTimeMs, p.text.joinToString("\n"), null))
        }
        while (sIdx < sList.size) {
            val s = sList[sIdx++]
            result.add(DualSubtitleCue(s.startTimeMs, s.endTimeMs, null, s.text.joinToString("\n")))
        }

        return result
    }
}

class DualSubtitleAdapter(
    private var currentTimeMs: Long,
    val clickCallback: (DualSubtitleCue) -> Unit
) : NoStateAdapter<DualSubtitleCue>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    a.startTimeMs == b.startTimeMs && a.endTimeMs == b.endTimeMs
})) {

    companion object {
        fun formatTime(timeMs: Long): String {
            val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
            val seconds = totalSeconds % 60
            val minutes = totalSeconds / 60 % 60
            val hours = totalSeconds / 3600
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = DialogDualSubtitlesItemBinding.inflate(inflater, parent, false)
        return ViewHolderState(binding)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: DualSubtitleCue, position: Int) {
        val binding = holder.view as? DialogDualSubtitlesItemBinding ?: return

        binding.root.setOnClickListener {
            clickCallback.invoke(item)
        }

        binding.primarySubText.text = item.primaryText ?: "—"
        binding.secondarySubText.text = item.secondaryText ?: "—"
        binding.timestampBadge.text = formatTime(item.startTimeMs)

        val timeMs = currentTimeMs
        val startTime = item.startTimeMs
        val endTime = item.endTimeMs

        val isActive = timeMs in startTime..<endTime
        val newAlpha = if (isActive || timeMs >= startTime) 1.0f else 0.5f
        binding.root.alpha = newAlpha

        binding.dualSubProgress.isInvisible = !isActive
        if (isActive && endTime > startTime) {
            val progressValue = ((timeMs - startTime) * 1000f / (endTime - startTime)).roundToInt()
            ObjectAnimator.ofInt(
                binding.dualSubProgress,
                "progress",
                binding.dualSubProgress.progress,
                progressValue
            ).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
            }.start()
        } else {
            binding.dualSubProgress.progress = 0
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

        val affectedItems = immutableCurrentList.withIndex().filter { cue ->
            cue.value.startTimeMs in (earlyTime - 5000)..(lateTime + 5000)
        }

        affectedItems.forEach { item ->
            this.notifyItemChanged(item.index)
        }
    }
}
