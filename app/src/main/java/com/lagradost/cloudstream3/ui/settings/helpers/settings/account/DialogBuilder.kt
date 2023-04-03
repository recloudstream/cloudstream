package com.lagradost.cloudstream3.ui.settings.helpers.settings.account

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.ui.settings.SettingsFragment
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

abstract class DialogBuilder(
    private val api: AuthAPI,
    private val activity: FragmentActivity?,
    private val themeResId: Int,
    private val layoutResId: Int,
) {
    class CommonDialogItems(
        private val dialog: AlertDialog,
        private val title: TextView,
        private val btnApply: MaterialButton,
        private val btnCancel: MaterialButton,
        private val btnAccCreate: MaterialButton?,
        private val btnConfirmOauth: MaterialButton?
    ) {
        fun getTitle() = dialog.getCommonItem(title)!!
        fun getBtnApply() = dialog.getCommonItem(btnApply)!!
        fun getBtnCancel() = dialog.getCommonItem(btnCancel)!!
        fun getBtnAccCreate() = dialog.getCommonItem(btnAccCreate)
        fun getBtnConfirm() = dialog.getCommonItem(btnConfirmOauth)

        private fun <T : View> AlertDialog.getCommonItem(view: T?): T? {
            return findViewById(view?.id ?: return null)
        }
    }


    abstract fun getCommonItems(dialog: AlertDialog): CommonDialogItems
    abstract fun getVisibilityMap(dialog: AlertDialog): Map<View, Boolean>
    abstract fun setupItems(dialog: AlertDialog)


    open fun handleStoresPasswordInPlainText(dialog: AlertDialog) {}
    open fun onDismiss(dialog: AlertDialog) {
        dialog.dismissSafe(activity)
    }

    open fun onLogin(dialog: AlertDialog) {
        dialog.dismissSafe(activity)
    }


    fun open(): AlertDialog? {
        if (activity == null) {
            return null
        }

        val dialogBuilder = AlertDialog.Builder(activity, themeResId).setView(layoutResId)
        val dialog = dialogBuilder.show()

        setup(dialog)
        handleStoresPasswordInPlainText(dialog)

        val commonItems = getCommonItems(dialog)
        commonItems.getTitle().text = api.name
        commonItems.getBtnApply().setOnClickListener { onLogin(dialog) }
        commonItems.getBtnCancel().setOnClickListener { onDismiss(dialog) }

        return dialog
    }


    protected fun setup(dialog: AlertDialog) {
        setItemVisibility(dialog)
        setupItems(dialog)
        linkItems(dialog)
    }

    private fun setItemVisibility(dialog: AlertDialog) {
        val visibilityMap = getVisibilityMap(dialog)

        if (SettingsFragment.isTvSettings()) {
            visibilityMap.forEach { (input, isVisible) ->
                input.isVisible = isVisible

                if (input !is TextView) {
                    return@forEach
                }

                // Band-aid for weird FireTV behavior causing crashes because keyboard covers the screen
                input.setOnEditorActionListener { textView, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_NEXT) {
                        val view = textView.focusSearch(View.FOCUS_DOWN)
                        return@setOnEditorActionListener view?.requestFocus(
                            View.FOCUS_DOWN
                        ) == true
                    }
                    return@setOnEditorActionListener true
                }
            }
        } else {
            visibilityMap.forEach { (input, isVisible) ->
                input.isVisible = isVisible
            }
        }
    }

    private fun linkItems(dialog: AlertDialog) = with(dialog) {
        val displayedItems = getVisibilityMap(dialog).keys.filter { it.isVisible }

        displayedItems.foldRight(displayedItems.firstOrNull()) { item, previous ->
            item?.id?.let { previous?.nextFocusDownId = it }
            previous?.id?.let { item?.nextFocusUpId = it }
            item
        }

        displayedItems.firstOrNull()?.let {
            val createAccount = getCommonItems(dialog).getBtnAccCreate() ?: return@let
            createAccount.nextFocusDownId = it.id
            it.nextFocusUpId = createAccount.id
        }

        getCommonItems(dialog).getBtnApply().id.let {
            displayedItems.lastOrNull()?.nextFocusDownId = it
        }

    }
}