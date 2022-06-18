package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.UIHelper.setImage

object SingleSelectionHelper {
    fun Activity?.showOptionSelectStringRes(
        view: View?,
        poster: String?,
        options: List<Int>,
        tvOptions: List<Int> = listOf(),
        callback: (Pair<Boolean, Int>) -> Unit
    ) {
        if(this == null) return

        this.showOptionSelect(
            view,
            poster,
            options.map { this.getString(it) },
            tvOptions.map { this.getString(it) },
            callback
        )
    }

    private fun Activity?.showOptionSelect(
        view: View?,
        poster: String?,
        options: List<String>,
        tvOptions: List<String>,
        callback: (Pair<Boolean, Int>) -> Unit
    ) {
        if(this == null) return

        if (this.isTvSettings()) {
            val builder =
                AlertDialog.Builder(this, R.style.AlertDialogCustom)
                    .setView(R.layout.options_popup_tv)

            val dialog = builder.create()
            dialog.show()

            dialog.findViewById<ListView>(R.id.listview1)?.let { listView ->
                listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                listView.adapter =
                    ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice_color).apply {
                        addAll(tvOptions)
                    }

                listView.setOnItemClickListener { _, _, i, _ ->
                    callback.invoke(Pair(true, i))
                    dialog.dismissSafe(this)
                }
            }

            dialog.findViewById<ImageView>(R.id.imageView)?.apply {
                isGone = poster.isNullOrEmpty()
                setImage(poster)
            }
        } else {
            view?.popupMenuNoIconsAndNoStringRes(options.mapIndexed { index, s ->
                Pair(
                    index,
                    s
                )
            }) {
                callback(Pair(false, this.itemId))
            }
        }
    }

    fun Activity?.showDialog(
        dialog: Dialog,
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        showApply: Boolean,
        isMultiSelect: Boolean,
        callback: (List<Int>) -> Unit,
        dismissCallback: () -> Unit
    ) {
        if(this == null) return

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


    private fun Activity?.showInputDialog(
        dialog: Dialog,
        value: String,
        name: String,
        textInputType: Int?,
        callback: (String) -> Unit,
        dismissCallback: () -> Unit
    ) {
        if(this == null) return

        val inputView = dialog.findViewById<EditText>(R.id.nginx_text_input)!!
        val textView = dialog.findViewById<TextView>(R.id.text1)!!
        val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
        val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
        val applyHolder = dialog.findViewById<LinearLayout>(R.id.apply_btt_holder)!!

        applyHolder.isVisible = true
        textView.text = name

        if (textInputType != null) {
            inputView.inputType = textInputType // 16 for website url input type
        }
        inputView.setText(value, TextView.BufferType.EDITABLE)


        applyButton.setOnClickListener {
            callback.invoke(inputView.text.toString())  // try to save the setting, using callback
            dialog.dismissSafe(this)
        }

        cancelButton.setOnClickListener {  // just dismiss
            dialog.dismissSafe(this)
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

    }

    fun Activity?.showMultiDialog(
        items: List<String>,
        selectedIndex: List<Int>,
        name: String,
        dismissCallback: () -> Unit,
        callback: (List<Int>) -> Unit,
    ) {
        if(this == null) return

        val builder =
            AlertDialog.Builder(this, R.style.AlertDialogCustom)
                .setView(R.layout.bottom_selection_dialog)

        val dialog = builder.create()
        dialog.show()
        showDialog(dialog, items, selectedIndex, name, true, true, callback, dismissCallback)
    }

    fun Activity?.showDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if(this == null) return

        val builder =
            AlertDialog.Builder(this, R.style.AlertDialogCustom)
                .setView(R.layout.bottom_selection_dialog)

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
    fun Activity?.showBottomDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (this == null) return
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
            { if (it.isNotEmpty()) callback.invoke(it.first()) },
            dismissCallback
        )
    }

    fun Activity.showNginxTextInputDialog(
        name: String,
        value: String,
        textInputType: Int?,
        dismissCallback: () -> Unit,
        callback: (String) -> Unit,
    ) {
        val builder = BottomSheetDialog(this)  // probably the stuff at the bottom
        builder.setContentView(R.layout.bottom_input_dialog)  // input layout

        builder.show()
        showInputDialog(
            builder,
            value,
            name,
            textInputType,  // type is a uri
            callback,
            dismissCallback
        )
    }
}
