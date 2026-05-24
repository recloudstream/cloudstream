package com.lagradost.cloudstream3.ui.sync

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DialogPinEntryBinding
import com.lagradost.cloudstream3.syncproviders.SyncProfile
import com.lagradost.cloudstream3.ui.BaseDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.security.MessageDigest

class PinEntryDialog : BaseDialogFragment<DialogPinEntryBinding>(
    BaseFragment.BindingCreator.Inflate(DialogPinEntryBinding::inflate)
) {
    companion object {
        const val TAG = "PinEntryDialog"
        private const val ARG_PROFILE = "arg_profile"

        fun newInstance(profile: SyncProfile): PinEntryDialog {
            val args = Bundle()
            args.putString(ARG_PROFILE, profile.toJson())
            val fragment = PinEntryDialog()
            fragment.arguments = args
            return fragment
        }
    }

    var onPinVerified: (() -> Unit)? = null

    private var profile: SyncProfile? = null
    private var enteredPin = StringBuilder()

    override fun fixLayout(view: View) {
        // Full screen safe implementation
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onBindingCreated(binding: DialogPinEntryBinding) {
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
        val currentProfile = profile ?: run {
            dismiss()
            return
        }

        binding.pinProfileName.text = currentProfile.name

        // Set avatar image
        val avatarName = currentProfile.avatarUrl ?: "avatar_1"
        val context = context ?: return
        val resId = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
        if (resId != 0) {
            binding.pinAvatar.setImageResource(resId)
        }

        // Set accent color border
        val accentColor = currentProfile.color ?: Color.parseColor("#E50914")
        binding.pinAvatarCard.strokeColor = accentColor

        setupNumpad()
    }

    private fun setupNumpad() {
        val binding = binding ?: return

        // Set click listeners for numbers 0-9
        val numberKeys = listOf(
            binding.key0 to "0",
            binding.key1 to "1",
            binding.key2 to "2",
            binding.key3 to "3",
            binding.key4 to "4",
            binding.key5 to "5",
            binding.key6 to "6",
            binding.key7 to "7",
            binding.key8 to "8",
            binding.key9 to "9"
        )

        for ((keyView, digit) in numberKeys) {
            keyView.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin.append(digit)
                    updatePinDots()
                    binding.pinErrorText.visibility = View.INVISIBLE

                    if (enteredPin.length == 4) {
                        verifyPin()
                    }
                }
            }
        }

        binding.keyBackspace.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
                binding.pinErrorText.visibility = View.INVISIBLE
            }
        }

        binding.keyCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updatePinDots() {
        val binding = binding ?: return
        val dots = listOf(binding.pinDot1, binding.pinDot2, binding.pinDot3, binding.pinDot4)
        
        for (i in dots.indices) {
            if (i < enteredPin.length) {
                dots[i].setImageResource(R.drawable.pin_dot_filled)
            } else {
                dots[i].setImageResource(R.drawable.pin_dot_empty)
            }
        }
    }

    private fun verifyPin() {
        val currentProfile = profile ?: return
        val pin = enteredPin.toString()
        val hashedInput = sha256(pin)

        if (hashedInput == currentProfile.pinHash) {
            onPinVerified?.invoke()
            dismiss()
        } else {
            // Shake dots to indicate wrong PIN
            shakeDots()
            
            // Show error message
            binding?.pinErrorText?.visibility = View.VISIBLE
            
            // Clear PIN
            enteredPin.clear()
            
            // Reset dots after a brief delay
            view?.postDelayed({
                updatePinDots()
            }, 300)
        }
    }

    private fun shakeDots() {
        val firstDot = binding?.pinDot1 ?: return
        val container = firstDot.parent as? View ?: return
        
        ObjectAnimator.ofFloat(
            container, 
            "translationX", 
            0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f
        ).apply {
            duration = 500
            start()
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
