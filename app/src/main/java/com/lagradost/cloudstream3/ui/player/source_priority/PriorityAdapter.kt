package com.lagradost.cloudstream3.ui.player.source_priority

import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.cloudstream3.databinding.PlayerPrioritizeItemBinding
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState

data class SourcePriority<T>(
    val data: T,
    val name: String,
    var priority: Int
)

class PriorityAdapter<T>() :
    NoStateAdapter<SourcePriority<T>>() {

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            PlayerPrioritizeItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SourcePriority<T>,
        position: Int
    ) {
        val binding = holder.view as? PlayerPrioritizeItemBinding ?: return
        binding.priorityText.text = item.name

        fun updatePriority() {
            binding.priorityNumber.text = item.priority.toString()
        }

        updatePriority()
        binding.addButton.setOnClickListener {
            // If someone clicks til the integer limit then they deserve to crash.
            item.priority++
            updatePriority()
        }

        binding.subtractButton.setOnClickListener {
            item.priority--
            updatePriority()
        }
    }
}