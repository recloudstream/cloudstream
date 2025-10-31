package com.lagradost.cloudstream3.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.databinding.ResultMiniImageBinding
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

const val IMAGE_CLICK = 0
const val IMAGE_LONG_CLICK = 1

class ImageAdapter(
    val clickCallback: ((Int) -> Unit)? = null,
    val nextFocusUp: Int? = null,
    val nextFocusDown: Int? = null,
) :
    NoStateAdapter<Int>(
        diffCallback = BaseDiffCallback(
            itemSame = Int::equals,
            contentSame = Int::equals
        )
    ) {
    companion object {
        val sharedPool =
            RecyclerView.RecycledViewPool().apply { this.setMaxRecycledViews(CONTENT, 10) }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ResultMiniImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        val binding = holder.view as? ResultMiniImageBinding ?: return
        clearImage(binding.root)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: Int, position: Int) {
        val binding = holder.view as? ResultMiniImageBinding ?: return

        binding.root.apply {
            loadImage(item)
            if (nextFocusDown != null) {
                this.nextFocusDownId = nextFocusDown
            }
            if (nextFocusUp != null) {
                this.nextFocusUpId = nextFocusUp
            }
            if (clickCallback != null) {
                if (isLayout(TV)) {
                    isClickable = true
                    isLongClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                setOnClickListener {
                    clickCallback.invoke(IMAGE_CLICK)
                }
                setOnLongClickListener {
                    clickCallback.invoke(IMAGE_LONG_CLICK)
                    return@setOnLongClickListener true
                }
            }
        }
    }
}