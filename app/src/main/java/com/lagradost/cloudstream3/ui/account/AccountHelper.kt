package com.lagradost.cloudstream3.ui.account

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountEditDialogBinding
import com.lagradost.cloudstream3.databinding.AccountSelectLinearBinding
import com.lagradost.cloudstream3.databinding.BottomInputDialogBinding
import com.lagradost.cloudstream3.databinding.LockPinDialogBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod

object AccountHelper {
    fun showAccountEditDialog(
        context: Context,
        account: DataStoreHelper.Account,
        isNewAccount: Boolean,
        accountEditCallback: (DataStoreHelper.Account) -> Unit,
        accountDeleteCallback: (DataStoreHelper.Account) -> Unit
    ) {
        val binding = AccountEditDialogBinding.inflate(LayoutInflater.from(context), null, false)
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(binding.root)

        var currentEditAccount = account
        val dialog = builder.show()

        if (!isNewAccount) binding.title.setText(R.string.edit_account)

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
                        accountDeleteCallback.invoke(account)
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
        binding.accountImage.loadImage(account.image)
        binding.accountImage.setOnClickListener {
            // Roll the image forwards once
            currentEditAccount = currentEditAccount.copy(customImage = null)
            currentEditAccount =
                currentEditAccount.copy(defaultImageIndex = (currentEditAccount.defaultImageIndex + 1) % DataStoreHelper.profileImages.size)
            binding.accountImage.loadImage(currentEditAccount.image)
        }

        // Handle applying changes
        binding.applyBtt.setOnClickListener {
            if (currentEditAccount.lockPin != null) {
                // Ask for the current PIN
                showPinInputDialog(context, currentEditAccount.lockPin, false) { pin ->
                    if (pin == null) return@showPinInputDialog
                    // PIN is correct, proceed to update the account
                    accountEditCallback.invoke(currentEditAccount)
                    dialog.dismissSafe()
                }
            } else {
                // No lock PIN set, proceed to update the account
                accountEditCallback.invoke(currentEditAccount)
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

        binding.editProfilePhotoButton.setOnClickListener({
            val bottomSheetDialog = BottomSheetDialog(context)
            val sheetBinding = BottomInputDialogBinding.inflate(LayoutInflater.from(context))
            bottomSheetDialog.setContentView(sheetBinding.root)
            bottomSheetDialog.show()

            sheetBinding.apply {
                text1.text = context.getString(R.string.edit_profile_image_title)
                nginxTextInput.hint = context.getString(R.string.edit_profile_image_hint)

                applyBtt.setOnClickListener({
                    val url = sheetBinding.nginxTextInput.text.toString()
                    if (url.isNotEmpty()) {
                        val imageLoader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .allowHardware(false)
                            .listener(
                                onSuccess = { _, _ ->
                                    currentEditAccount = currentEditAccount.copy(customImage = url)
                                    binding.accountImage.loadImage(url)
                                    showToast(
                                        R.string.edit_profile_image_success,
                                        Toast.LENGTH_SHORT
                                    )
                                    bottomSheetDialog.dismiss()
                                },
                                onError = { _, _ ->
                                    showToast(
                                        R.string.edit_profile_image_error_invalid,
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            )
                            .build()
                        imageLoader.enqueue(request)
                    } else {
                        showToast(R.string.edit_profile_image_error_empty, Toast.LENGTH_SHORT)
                    }
                    bottomSheetDialog.dismissSafe()
                })
                sheetBinding.cancelBtt.setOnClickListener({
                    bottomSheetDialog.dismissSafe()
                })
            }
        })
    }

    fun showPinInputDialog(
        context: Context,
        currentPin: String?,
        editAccount: Boolean,
        forStartup: Boolean = false,
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

        if (forStartup) {
            val currentAccount = DataStoreHelper.accounts.firstOrNull {
                it.keyIndex == DataStoreHelper.selectedKeyIndex
            }

            builder.setTitle(context.getString(R.string.enter_pin_with_name, currentAccount?.name))
            builder.setOnDismissListener {
                if (!isPinValid) {
                    context.getActivity()?.finish()
                }
            }
            // So that if they don't know the PIN for the current account,
            // they don't get completely locked out
            builder.setNeutralButton(R.string.use_default_account) { _, _ ->
                val activity = context.getActivity()
                if (activity is AccountSelectActivity) {
                    isPinValid = true
                    activity.accountViewModel.handleAccountSelect(getDefaultAccount(context), activity)
                }
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

    fun Activity?.showAccountSelectLinear() {
        val activity = this as? MainActivity ?: return
        val viewModel = ViewModelProvider(activity)[AccountViewModel::class.java]

        val binding: AccountSelectLinearBinding = AccountSelectLinearBinding.inflate(
            LayoutInflater.from(activity)
        )

        val builder = BottomSheetDialog(activity)
        builder.setContentView(binding.root)
        builder.show()

        binding.manageAccountsButton.setOnClickListener {
            activity.navigate(
                R.id.accountSelectActivity,
                Bundle().apply { putBoolean("isEditingFromMainActivity", true) }
            )
            builder.dismissSafe()
        }

        val recyclerView: RecyclerView = binding.accountRecyclerView

        val itemSize = recyclerView.resources.getDimensionPixelSize(
            R.dimen.account_select_linear_item_size
        )

        recyclerView.addItemDecoration(AccountSelectLinearItemDecoration(itemSize))

        recyclerView.setLinearListLayout(isHorizontal = true)

        val currentAccount = DataStoreHelper.accounts.firstOrNull {
            it.keyIndex == DataStoreHelper.selectedKeyIndex
        } ?: getDefaultAccount(activity)

        // We want to make sure the accounts are up-to-date
        viewModel.handleAccountSelect(
            currentAccount,
            activity,
            reloadForActivity = true
        )

        activity.observe(viewModel.accounts) { liveAccounts ->
            recyclerView.adapter = AccountAdapter(
                liveAccounts,
                accountSelectCallback = { account ->
                    viewModel.handleAccountSelect(account, activity)
                    builder.dismissSafe()
                },
                accountCreateCallback = { viewModel.handleAccountUpdate(it, activity) },
                accountEditCallback = { viewModel.handleAccountUpdate(it, activity) },
                accountDeleteCallback = { viewModel.handleAccountDelete(it, activity) }
            )

            activity.observe(viewModel.selectedKeyIndex) { selectedKeyIndex ->
                // Scroll to current account (which is focused by default)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(selectedKeyIndex, 0)
            }
        }
    }
}