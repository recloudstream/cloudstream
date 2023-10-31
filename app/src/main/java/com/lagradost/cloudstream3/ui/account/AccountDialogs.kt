package com.lagradost.cloudstream3.ui.account

import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKeys
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountEditDialogBinding
import com.lagradost.cloudstream3.databinding.LockPinDialogBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.result.setImage
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.setAccount
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

object AccountDialogs {
    fun showAccountEditDialog(
        context: Context,
        account: DataStoreHelper.Account,
        isNewAccount: Boolean,
        callback: (DataStoreHelper.Account) -> Unit
    ) {
        val binding = AccountEditDialogBinding.inflate(LayoutInflater.from(context), null, false)
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(binding.root)

        var currentEditAccount = account
        val dialog = builder.show()

        // Set up the dialog content
        binding.accountName.text = Editable.Factory.getInstance()?.newEditable(account.name)
        binding.accountName.doOnTextChanged { text, _, _, _ ->
            currentEditAccount = currentEditAccount.copy(name = text?.toString() ?: "")
        }

        binding.deleteBtt.isGone = isNewAccount
        binding.deleteBtt.setOnClickListener {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        // Remove the account
                        removeKeys(account.keyIndex.toString())
                        val currentAccounts = DataStoreHelper.accounts.toMutableList()
                        currentAccounts.removeIf { it.keyIndex == account.keyIndex }
                        DataStoreHelper.accounts = currentAccounts.toTypedArray()

                        // Update UI
                        setAccount(getDefaultAccount(context), true)
                        dialog?.dismissSafe()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                        dialog?.dismissSafe()
                    }
                }
            }

            try {
                AlertDialog.Builder(context).setTitle(R.string.delete).setMessage(
                    context.getString(R.string.delete_message).format(
                        currentEditAccount.name
                    )
                )
                    .setPositiveButton(R.string.delete, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show().setDefaultFocus()
            } catch (t: Throwable) {
                logError(t)
            }
        }

        binding.cancelBtt.setOnClickListener {
            dialog?.dismissSafe()
        }

        // Handle the profile picture and its interactions
        binding.profilePic.setImage(account.image)
        binding.profilePic.setOnClickListener {
            // Roll the image forwards once
            currentEditAccount =
                currentEditAccount.copy(defaultImageIndex = (currentEditAccount.defaultImageIndex + 1) % DataStoreHelper.profileImages.size)
            binding.profilePic.setImage(currentEditAccount.image)
        }

        // Handle applying changes
        binding.applyBtt.setOnClickListener {
            if (currentEditAccount.lockPin != null) {
                // Ask for the current PIN
                showPinInputDialog(context, currentEditAccount.lockPin, false) { pin ->
                    if (pin == null) return@showPinInputDialog
                    // PIN is correct, proceed to update the account
                    callback.invoke(currentEditAccount)
                    dialog.dismissSafe()
                }
            } else {
                // No lock PIN set, proceed to update the account
                callback.invoke(currentEditAccount)
                dialog.dismissSafe()
            }
        }

        // Handle setting or changing the PIN
        if (currentEditAccount.keyIndex == getDefaultAccount(context).keyIndex) {
            binding.lockProfileCheckbox.isVisible = false
            if (currentEditAccount.lockPin != null) {
                currentEditAccount = currentEditAccount.copy(lockPin = null)
            }
        }

        var canSetPin = true

        binding.lockProfileCheckbox.isChecked = currentEditAccount.lockPin != null

        binding.lockProfileCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (canSetPin) {
                    showPinInputDialog(context, null, true) { pin ->
                        if (pin == null) {
                            binding.lockProfileCheckbox.isChecked = false
                            return@showPinInputDialog
                        }

                        currentEditAccount = currentEditAccount.copy(lockPin = pin)
                    }
                }
            } else {
                if (currentEditAccount.lockPin != null) {
                    // Ask for the current PIN
                    showPinInputDialog(context, currentEditAccount.lockPin, true) { pin ->
                        if (pin == null || pin != currentEditAccount.lockPin) {
                            canSetPin = false
                            binding.lockProfileCheckbox.isChecked = true
                        } else {
                            currentEditAccount = currentEditAccount.copy(lockPin = null)
                        }
                    }
                }
            }
        }

        canSetPin = true
    }

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
                if (binding.pinEditTextError.isVisible) {
                    callback.invoke(null)
                }
            }

        if (isNewPin) {
            if (errorText != null) binding.pinEditTextError.visibleWithText(errorText)
            builder.setPositiveButton(R.string.setup_done) { _, _ ->
                if (binding.pinEditTextError.isVisible) {
                    // If the done button is pressed and there is a error,
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

        var isPinValid = false

        binding.pinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val enteredPin = s.toString()
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

            override fun afterTextChanged(s: Editable?) {}
        })

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
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.pinEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }
}