package com.lagradost.cloudstream3.utils

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.R

object SingleSelectionHelper {
    fun Context.showDialog(
        dialog: Dialog,
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        callback: (Int) -> Unit,
        dismissCallback: () -> Unit
    ) {
        val listView = dialog.findViewById<ListView>(R.id.listview1)!!
        val textView = dialog.findViewById<TextView>(R.id.text1)!!
        val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
        val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
        val applyHolder = dialog.findViewById<LinearLayout>(R.id.apply_btt_holder)!!

        applyHolder.visibility = if (showApply) View.VISIBLE else View.GONE
        if (!showApply) {
            val params = listView.layoutParams as LinearLayout.LayoutParams
            params.setMargins(listView.marginLeft, listView.marginTop, listView.marginRight, 0)
            listView.layoutParams = params
        }

        textView.text = name

        val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
        arrayAdapter.addAll(items)

        listView.adapter = arrayAdapter
        listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE

        listView.setSelection(selectedIndex)
        listView.setItemChecked(selectedIndex, true)

        var currentIndex = selectedIndex

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        listView.setOnItemClickListener { _, _, which, _ ->
            if (showApply) {
                currentIndex = which
                listView.setItemChecked(which, true)
            } else {
                callback.invoke(which)
                dialog.dismiss()
            }
        }
        if (showApply) {
            applyButton.setOnClickListener {
                callback.invoke(currentIndex)
                dialog.dismiss()
            }
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    fun Context.showDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        val builder =
            AlertDialog.Builder(this, R.style.AlertDialogCustom).setView(R.layout.bottom_selection_dialog)

        val dialog = builder.create()
        dialog.show()
        showDialog(dialog, items, selectedIndex, name, showApply, callback, dismissCallback)
    }

    fun Context.showBottomDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        val builder =
            BottomSheetDialog(this)
        builder.setContentView(R.layout.bottom_selection_dialog)

        builder.show()
        showDialog(builder, items, selectedIndex, name, showApply, callback, dismissCallback)
    }
}