package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.Dialog
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe

object SingleSelectionHelper {
    fun Activity.showDialog(
        dialog: Dialog,
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        showApply: Boolean,
        isMultiSelect: Boolean,
        callback: (List<Int>) -> Unit,
        dismissCallback: () -> Unit
    ) {
        val realShowApply = showApply || isMultiSelect
        val listView = dialog.findViewById<ListView>(R.id.listview1)!!
        val textView = dialog.findViewById<TextView>(R.id.text1)!!
        val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
        val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
        val applyHolder = dialog.findViewById<LinearLayout>(R.id.apply_btt_holder)!!

        applyHolder.isVisible = realShowApply
        if (!realShowApply) {
            val params = listView.layoutParams as LinearLayout.LayoutParams
            params.setMargins(listView.marginLeft, listView.marginTop, listView.marginRight, 0)
            listView.layoutParams = params
        }

        textView.text = name

        val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
        arrayAdapter.addAll(items)

        listView.adapter = arrayAdapter
        if (isMultiSelect) {
            listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE
        } else {
            listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        }

        for (select in selectedIndex) {
            listView.setItemChecked(select, true)
        }

        selectedIndex.minOrNull()?.let {
            listView.setSelection(it)
        }

        //  var lastSelectedIndex = if(selectedIndex.isNotEmpty()) selectedIndex.first() else -1

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        listView.setOnItemClickListener { _, _, which, _ ->
            //  lastSelectedIndex = which
            if (realShowApply) {
                if (!isMultiSelect) {
                    listView.setItemChecked(which, true)
                }
            } else {
                callback.invoke(listOf(which))
                dialog.dismissSafe(this)
            }
        }
        if (realShowApply) {
            applyButton.setOnClickListener {
                val list = ArrayList<Int>()
                for (index in 0 until listView.count) {
                    if (listView.checkedItemPositions[index])
                        list.add(index)
                }
                callback.invoke(list)
                dialog.dismissSafe(this)
            }
            cancelButton.setOnClickListener {
                dialog.dismissSafe(this)
            }
        }
    }

    fun Activity.showMultiDialog(
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        dismissCallback: () -> Unit,
        callback: (List<Int>) -> Unit,
    ) {
        val builder =
            AlertDialog.Builder(this, R.style.AlertDialogCustom).setView(R.layout.bottom_selection_dialog)

        val dialog = builder.create()
        dialog.show()
        showDialog(dialog, items, selectedIndex, name, true, true, callback, dismissCallback)
    }

    fun Activity.showDialog(
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
        showDialog(
            dialog,
            items,
            listOf(selectedIndex),
            name,
            showApply,
            false,
            { if (it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback
        )
    }

    /** Only for a low amount of items */
    fun Activity.showBottomDialog(
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
        showDialog(
            builder,
            items,
            listOf(selectedIndex),
            name,
            showApply,
            false,
            { if(it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback
        )
    }
}