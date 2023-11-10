package com.lagradost.cloudstream3.ui.account

import android.content.Context
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.LockPinDialogBinding
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod

object AccountDialog {
    // TODO add account creation dialog to allow creating accounts directly from AccountSelectActivity

    fun showPinInputDialog(
        context: Context,
        currentPin: String?,
        editAccount: Boolean,
        errorText: String? = null,
        callback: (String?) -> Unit
    ) {
        fun TextView.visibleWithText(@StringRes textRes: Int) {
            isVisible = true
            setText(textRes)
        }

        fun TextView.visibleWithText(text: String?) {
            isVisible = true
            setText(text)
        }

        val binding = LockPinDialogBinding.inflate(LayoutInflater.from(context))

        val isPinSet = currentPin != null
        val isNewPin = editAccount && !isPinSet
        val isEditPin = editAccount && isPinSet

        val titleRes = if (isEditPin) R.string.enter_current_pin else R.string.enter_pin

        var isPinValid = false

        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(binding.root)
            .setTitle(titleRes)
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback.invoke(null)
            }
            .setOnCancelListener {
                callback.invoke(null)
            }
            .setOnDismissListener {
                if (!isPinValid) {
                    callback.invoke(null)
                }
            }

        if (isNewPin) {
            if (errorText != null) binding.pinEditTextError.visibleWithText(errorText)
            builder.setPositiveButton(R.string.setup_done) { _, _ ->
                if (!isPinValid) {
                    // If the done button is pressed and there is an error,
                    // ask again, and mention the error that caused this.
                    showPinInputDialog(
                        context = binding.root.context,
                        currentPin = null,
                        editAccount = true,
                        errorText = binding.pinEditTextError.text.toString(),
                        callback = callback
                    )
                } else {
                    val enteredPin = binding.pinEditText.text.toString()
                    callback.invoke(enteredPin)
                }
            }
        }

        val dialog = builder.create()

        binding.pinEditText.doOnTextChanged { text, _, _, _ ->
            val enteredPin = text.toString()
            val isEnteredPinValid = enteredPin.length == 4

            if (isEnteredPinValid) {
                if (isPinSet) {
                    if (enteredPin != currentPin) {
                        binding.pinEditTextError.visibleWithText(R.string.pin_error_incorrect)
                        binding.pinEditText.text = null
                        isPinValid = false
                    } else {
                        binding.pinEditTextError.isVisible = false
                        isPinValid = true

                        callback.invoke(enteredPin)
                        dialog.dismissSafe()
                    }
                } else {
                    binding.pinEditTextError.isVisible = false
                    isPinValid = true
                }
            } else if (isNewPin) {
                binding.pinEditTextError.visibleWithText(R.string.pin_error_length)
                isPinValid = false
            }
        }

        // Detect IME_ACTION_DONE
        binding.pinEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && isPinValid) {
                val enteredPin = binding.pinEditText.text.toString()
                callback.invoke(enteredPin)
                dialog.dismissSafe()
            }
            true
        }

        // We don't want to accidentally have the dialog dismiss when clicking outside of it.
        // That is what the cancel button is for.
        dialog.setCanceledOnTouchOutside(false)

        dialog.show()

        // Auto focus on PIN input and show keyboard
        binding.pinEditText.requestFocus()
        binding.pinEditText.postDelayed({
            showInputMethod(binding.pinEditText)
        }, 200)
    }
}