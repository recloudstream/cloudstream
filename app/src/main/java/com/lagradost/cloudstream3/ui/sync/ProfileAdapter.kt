package com.lagradost.cloudstream3.ui.sync

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ItemProfileBinding
import com.lagradost.cloudstream3.syncproviders.SyncProfile
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage

class ProfileAdapter(
    private val clickListener: (SyncProfile, Boolean) -> Unit
) : NoStateAdapter<SyncProfile>(
    diffCallback = BaseDiffCallback(
        itemSame = { a, b -> a.id == b.id },
        contentSame = { a, b -> 
            a.name == b.name && 
            a.avatarUrl == b.avatarUrl && 
            a.pinHash == b.pinHash && 
            a.color == b.color 
        }
    )
) {

    companion object {
        const val ADD_PROFILE_ID = "add_profile"
    }

    var isEditMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            ItemProfileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        val binding = holder.view as? ItemProfileBinding ?: return
        clearImage(binding.itemProfileAvatar)
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SyncProfile, position: Int) {
        val binding = holder.view as? ItemProfileBinding ?: return
        val context = binding.root.context

        binding.apply {
            if (item.id == ADD_PROFILE_ID) {
                // Add Profile Item Styling
                itemProfileName.text = "Add Profile"
                itemProfileAvatar.setImageResource(R.drawable.ic_baseline_add_24)
                itemProfileAvatar.setPadding(32, 32, 32, 32)
                itemProfileAvatar.imageTintList = ColorStateList.valueOf(
                    context.getColor(R.color.white)
                )
                
                // Dotted or simple gray stroke
                itemProfileCard.strokeColor = context.getColor(R.color.grayTextColor)
                itemProfileCard.strokeWidth = 2
                
                itemProfileLock.isVisible = false
                itemProfileEditOverlay.isVisible = false
                itemProfileAccent.setBackgroundColor(Color.TRANSPARENT)
            } else {
                // Regular Profile Item Styling
                itemProfileName.text = item.name
                itemProfileAvatar.setPadding(0, 0, 0, 0)
                itemProfileAvatar.imageTintList = null

                // Load custom avatar, resource avatar or fallback
                val avatarUrl = item.avatarUrl
                if (!avatarUrl.isNullOrEmpty()) {
                    val resId = context.resources.getIdentifier(avatarUrl, "drawable", context.packageName)
                    if (resId != 0) {
                        itemProfileAvatar.setImageResource(resId)
                    } else {
                        // In case of custom path (future-proofing) or if image library loader is needed
                        itemProfileAvatar.loadImage(avatarUrl)
                    }
                } else {
                    itemProfileAvatar.setImageResource(R.drawable.avatar_1)
                }

                // Show accent boundary color if specified
                val profileColor = item.color
                if (profileColor != null) {
                    itemProfileCard.strokeColor = profileColor
                    itemProfileCard.strokeWidth = 4
                    itemProfileAccent.setBackgroundColor((profileColor and 0x00FFFFFF) or 0x1A000000)
                } else {
                    itemProfileCard.strokeColor = Color.TRANSPARENT
                    itemProfileCard.strokeWidth = 0
                    itemProfileAccent.setBackgroundColor(Color.TRANSPARENT)
                }

                // Overlay status checks
                itemProfileLock.isVisible = item.isLocked
                itemProfileEditOverlay.isVisible = isEditMode
            }

            root.setOnClickListener {
                clickListener.invoke(item, isEditMode)
            }
        }
    }
}
