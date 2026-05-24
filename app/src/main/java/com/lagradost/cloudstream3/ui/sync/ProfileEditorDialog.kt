package com.lagradost.cloudstream3.ui.sync

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DialogProfileEditorBinding
import com.lagradost.cloudstream3.databinding.ItemAvatarSelectBinding
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncProfile
import com.lagradost.cloudstream3.ui.BaseDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.coroutines.launch
import java.security.MessageDigest

class ProfileEditorDialog : BaseDialogFragment<DialogProfileEditorBinding>(
    BaseFragment.BindingCreator.Inflate(DialogProfileEditorBinding::inflate)
) {
    companion object {
        const val TAG = "ProfileEditorDialog"
        private const val ARG_PROFILE = "arg_profile"

        fun newInstance(profile: SyncProfile?): ProfileEditorDialog {
            val args = Bundle()
            profile?.let {
                args.putString(ARG_PROFILE, it.toJson())
            }
            val fragment = ProfileEditorDialog()
            fragment.arguments = args
            return fragment
        }
    }

    var onProfileSaved: (() -> Unit)? = null

    private var profile: SyncProfile? = null
    
    // Available Netflix-style accent colors
    private val colors = arrayOf(
        Color.parseColor("#E50914"), // Red
        Color.parseColor("#54B4E4"), // Blue
        Color.parseColor("#E5007A"), // Pink
        Color.parseColor("#2CA01C"), // Green
        Color.parseColor("#8B2CA0"), // Purple
        Color.parseColor("#F57C00"), // Orange
        Color.parseColor("#FFC20E")  // Yellow
    )
    
    // 12 Avatar resources in drawable
    private val avatars = arrayOf(
        "avatar_1", "avatar_2", "avatar_3", "avatar_4",
        "avatar_5", "avatar_6", "avatar_7", "avatar_8",
        "avatar_9", "avatar_10", "avatar_11", "avatar_12"
    )

    private var selectedColor: Int = Color.parseColor("#E50914")
    private var selectedAvatar: String = "avatar_1"

    override fun fixLayout(view: View) {
        // Safe empty implementation, system bars handled by parent full-screen dialog container
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dialog style
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onBindingCreated(binding: DialogProfileEditorBinding) {
        // Parse profile argument if editing
        val profileJson = arguments?.getString(ARG_PROFILE)
        if (!profileJson.isNullOrEmpty()) {
            try {
                profile = parseJson<SyncProfile>(profileJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setupUI()
    }

    private fun setupUI() {
        val binding = binding ?: return
        val currentProfile = profile

        if (currentProfile != null) {
            // Edit Mode
            binding.profileEditorTitle.text = "Edit Profile"
            binding.profileEditorName.setText(currentProfile.name)
            
            selectedAvatar = currentProfile.avatarUrl ?: "avatar_1"
            selectedColor = currentProfile.color ?: Color.parseColor("#E50914")
            
            // Set PIN status
            binding.profileEditorPinSwitch.isChecked = currentProfile.isLocked
            binding.profileEditorPinSection.isVisible = currentProfile.isLocked
            if (currentProfile.isLocked) {
                binding.profileEditorPinInput.setHint("••••")
            }
            
            // Show delete button
            binding.profileEditorDeleteBtn.isVisible = true
        } else {
            // Create Mode
            binding.profileEditorTitle.text = "Create Profile"
            selectedAvatar = "avatar_1"
            selectedColor = Color.parseColor("#E50914")
            
            binding.profileEditorPinSwitch.isChecked = false
            binding.profileEditorPinSection.isVisible = false
            
            // Hide delete button
            binding.profileEditorDeleteBtn.isVisible = false
        }

        updateAvatarPreview()
        setupAvatarList()
        setupColorList()
        setupListeners()
    }

    private fun setupListeners() {
        val binding = binding ?: return
        
        binding.profileEditorPinSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.profileEditorPinSection.isVisible = isChecked
            if (!isChecked) {
                binding.profileEditorPinInput.text = null
            }
        }

        binding.profileEditorCancelBtn.setOnClickListener {
            dismiss()
        }

        binding.profileEditorSaveBtn.setOnClickListener {
            saveProfileData()
        }

        binding.profileEditorDeleteBtn.setOnClickListener {
            deleteProfileData()
        }
    }

    private fun updateAvatarPreview() {
        val binding = binding ?: return
        val context = context ?: return
        
        val resId = context.resources.getIdentifier(selectedAvatar, "drawable", context.packageName)
        if (resId != 0) {
            binding.profileEditorAvatarPreview.setImageResource(resId)
        }
        
        binding.profileEditorAvatarCard.strokeColor = selectedColor
    }

    private fun setupAvatarList() {
        val binding = binding ?: return
        val context = context ?: return
        
        val layoutManager = GridLayoutManager(context, 4, RecyclerView.VERTICAL, false)
        binding.profileEditorAvatarList.layoutManager = layoutManager
        
        val adapter = object : RecyclerView.Adapter<AvatarViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
                val itemBinding = ItemAvatarSelectBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return AvatarViewHolder(itemBinding)
            }

            override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
                val avatarName = avatars[position]
                val ctx = holder.binding.root.context
                val resId = ctx.resources.getIdentifier(avatarName, "drawable", ctx.packageName)
                if (resId != 0) {
                    holder.binding.itemAvatarImage.setImageResource(resId)
                }

                val isSelected = selectedAvatar == avatarName
                holder.binding.itemAvatarCard.strokeWidth = if (isSelected) 3.toPx else 0
                holder.binding.itemAvatarCard.strokeColor = selectedColor

                holder.binding.root.setOnClickListener {
                    val prevSelectedName = selectedAvatar
                    selectedAvatar = avatarName
                    updateAvatarPreview()
                    
                    // Notify items to redraw
                    notifyDataSetChanged()
                }
            }

            override fun getItemCount(): Int = avatars.size
        }
        
        binding.profileEditorAvatarList.adapter = adapter
    }

    private fun setupColorList() {
        val binding = binding ?: return
        val context = context ?: return
        
        binding.profileEditorColorList.removeAllViews()
        
        for (color in colors) {
            val card = MaterialCardView(context).apply {
                val size = 44.toPx
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(8.toPx, 4.toPx, 8.toPx, 4.toPx)
                }
                radius = 22.toPx.toFloat()
                setCardBackgroundColor(ColorStateList.valueOf(color))
                setCardElevation(0f)
                strokeWidth = if (selectedColor == color) 3.toPx else 0
                strokeColor = Color.WHITE
                
                setOnClickListener {
                    selectedColor = color
                    updateAvatarPreview()
                    
                    // Re-render color list to show selection border
                    setupColorList()
                    
                    // Redraw avatars list to match new accent color
                    binding.profileEditorAvatarList.adapter?.notifyDataSetChanged()
                }
            }
            binding.profileEditorColorList.addView(card)
        }
    }

    private fun saveProfileData() {
        val binding = binding ?: return
        val name = binding.profileEditorName.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.profileEditorNameLayout.error = "Name cannot be empty"
            return
        }
        
        binding.profileEditorNameLayout.error = null
        val pinInput = binding.profileEditorPinInput.text.toString().trim()
        var pinHashResult: String? = profile?.pinHash

        if (binding.profileEditorPinSwitch.isChecked) {
            if (profile?.isLocked == true && pinInput.isEmpty()) {
                // Keeping existing locked PIN, pinHash remains as is
            } else if (pinInput.length != 4) {
                Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                return
            } else {
                // New or updated PIN
                pinHashResult = sha256(pinInput)
            }
        } else {
            // Disabled lock PIN
            pinHashResult = null
        }

        lifecycleScope.launch {
            binding.profileEditorSaveBtn.isEnabled = false
            binding.profileEditorCancelBtn.isEnabled = false
            
            try {
                val targetProfile = profile ?: SyncProfile()
                targetProfile.name = name
                targetProfile.avatarUrl = selectedAvatar
                targetProfile.color = selectedColor
                targetProfile.pinHash = pinHashResult
                
                val success = AccountManager.firebaseApi.saveProfile(targetProfile)
                if (success) {
                    Toast.makeText(context, "Profile saved!", Toast.LENGTH_SHORT).show()
                    onProfileSaved?.invoke()
                    dismiss()
                } else {
                    Toast.makeText(context, "Failed to save profile", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.profileEditorSaveBtn.isEnabled = true
                binding.profileEditorCancelBtn.isEnabled = true
            }
        }
    }

    private fun deleteProfileData() {
        val currentProfile = profile ?: return
        val binding = binding ?: return
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Delete Profile")
            .setMessage("Are you sure you want to delete '${currentProfile.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    binding.profileEditorDeleteBtn.isEnabled = false
                    try {
                        val success = AccountManager.firebaseApi.deleteProfile(currentProfile.id)
                        if (success) {
                            Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show()
                            onProfileSaved?.invoke()
                            dismiss()
                        } else {
                            Toast.makeText(context, "Failed to delete profile", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        binding.profileEditorDeleteBtn.isEnabled = true
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    class AvatarViewHolder(val binding: ItemAvatarSelectBinding) : RecyclerView.ViewHolder(binding.root)
}
